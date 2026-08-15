package cc.netwokx.ffsms.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class GroupWithCount(
    @Embedded val group: GroupEntity,
    val recipientCount: Int,
    val invalidCount: Int,
)

@Dao
interface GroupDao {

    @Query(
        """
        SELECT g.*,
               (SELECT COUNT(*) FROM recipients r WHERE r.groupId = g.id) AS recipientCount,
               (SELECT COUNT(*) FROM recipients r WHERE r.groupId = g.id AND r.valid = 0) AS invalidCount
        FROM groups g
        ORDER BY g.name COLLATE NOCASE
        """,
    )
    fun observeGroups(): Flow<List<GroupWithCount>>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun findById(id: Long): GroupEntity?

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Query("UPDATE groups SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """
        UPDATE groups
        SET contactGroupId = :contactGroupId, contactGroupTitle = :title,
            autoSyncContacts = :autoSync
        WHERE id = :id
        """,
    )
    suspend fun linkContactGroup(id: Long, contactGroupId: Long?, title: String?, autoSync: Boolean)

    @Query("UPDATE groups SET autoSyncContacts = :enabled WHERE id = :id")
    suspend fun setAutoSync(id: Long, enabled: Boolean)

    @Query("UPDATE groups SET lastContactSyncAt = :at WHERE id = :id")
    suspend fun markSynced(id: Long, at: Long)

    /** Alle Verteiler, die taeglich mit einer Kontaktgruppe abgeglichen werden. */
    @Query("SELECT * FROM groups WHERE contactGroupId IS NOT NULL AND autoSyncContacts = 1")
    suspend fun autoSyncGroups(): List<GroupEntity>
}

@Dao
interface RecipientDao {

    @Query("SELECT * FROM recipients WHERE groupId = :groupId ORDER BY valid ASC, displayName COLLATE NOCASE, msisdn")
    fun observeForGroup(groupId: Long): Flow<List<RecipientEntity>>

    @Query("SELECT * FROM recipients WHERE groupId = :groupId AND valid = 1 ORDER BY displayName COLLATE NOCASE, msisdn")
    suspend fun validForGroup(groupId: Long): List<RecipientEntity>

    /**
     * IGNORE statt REPLACE: eine bereits vorhandene Nummer wird verworfen und
     * die Methode liefert -1 zurueck. So kann der Import "3 Duplikate
     * uebersprungen" melden, ohne vorher selbst zu pruefen (was eine
     * Race Condition waere).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicates(recipient: RecipientEntity): Long

    @Query("DELETE FROM recipients WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM recipients WHERE groupId = :groupId AND valid = 1")
    suspend fun countValid(groupId: Long): Int

    @Query("SELECT * FROM recipients WHERE groupId = :groupId")
    suspend fun allForGroup(groupId: Long): List<RecipientEntity>

    /**
     * Markiert Empfaenger, die nicht mehr in der Kontaktgruppe stehen.
     * Entfernt wird nichts - darueber entscheidet ein Mensch.
     */
    @Query("UPDATE recipients SET missingInContactGroup = :missing WHERE id = :id")
    suspend fun setMissing(id: Long, missing: Boolean)

    @Query("DELETE FROM recipients WHERE groupId = :groupId AND missingInContactGroup = 1")
    suspend fun deleteMissing(groupId: Long)

    @Query("SELECT COUNT(*) FROM recipients WHERE groupId = :groupId AND missingInContactGroup = 1")
    suspend fun countMissing(groupId: Long): Int
}

data class RecipientStatusRow(
    val msisdn: String,
    val displayName: String?,
    val position: Int,
    val status: String?,
    val segments: Int?,
    val errorCode: Int?,
    val attempt: Int?,
    val updatedAt: Long?,
)

@Dao
interface CampaignDao {

    @Query("SELECT * FROM campaigns ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns WHERE id = :id")
    fun observeById(id: String): Flow<CampaignEntity?>

    @Query("SELECT * FROM campaigns WHERE id = :id")
    suspend fun findById(id: String): CampaignEntity?

    @Query("SELECT * FROM campaigns ORDER BY createdAt DESC")
    suspend fun all(): List<CampaignEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(campaign: CampaignEntity): Long

    @Update
    suspend fun update(campaign: CampaignEntity)

    @Query("UPDATE campaigns SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: CampaignStatus)

    @Query(
        """
        UPDATE campaigns
        SET status = :status, failedCount = :failed, abortedReason = :reason, finishedAt = :finishedAt
        WHERE id = :id
        """,
    )
    suspend fun finish(
        id: String,
        status: CampaignStatus,
        failed: Int,
        reason: String?,
        finishedAt: Long,
    )

    /** IGNORE, damit ein zweiter Bestaetigungsklick nicht in eine Exception laeuft. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipients(recipients: List<CampaignRecipientEntity>)

    @Query("SELECT * FROM campaign_recipients WHERE campaignId = :campaignId ORDER BY position")
    suspend fun recipientsFor(campaignId: String): List<CampaignRecipientEntity>

    /**
     * Empfaengerliste einer Kampagne mit dem jeweiligen Sendestatus.
     * LEFT JOIN, weil ein Empfaenger ohne Log-Zeile schlicht noch nicht
     * an der Reihe war.
     */
    @Query(
        """
        SELECT cr.msisdn AS msisdn,
               cr.displayName AS displayName,
               cr.position AS position,
               sl.status AS status,
               sl.segments AS segments,
               sl.errorCode AS errorCode,
               sl.attempt AS attempt,
               sl.updatedAt AS updatedAt
        FROM campaign_recipients cr
        LEFT JOIN send_log sl
               ON sl.campaignId = cr.campaignId AND sl.msisdn = cr.msisdn
        WHERE cr.campaignId = :campaignId
        ORDER BY cr.position
        """,
    )
    fun observeRecipientStatus(campaignId: String): Flow<List<RecipientStatusRow>>

    // --- Verbrauch (funktioniert ohne Backend) ---------------------------

    /**
     * Tatsaechlich abgesetzte Segmente in einem Zeitraum.
     *
     * Bewusst aus send_log und nicht aus campaigns.totalSegments: gezaehlt
     * wird, was das Modem wirklich gesendet hat, nicht was geplant war. Eine
     * abgebrochene Kampagne taucht damit nur mit ihrem tatsaechlichen
     * Verbrauch auf.
     */
    @Query(
        """
        SELECT COALESCE(SUM(segments), 0) FROM send_log
        WHERE createdAt >= :from AND createdAt < :to
          AND status IN ('SENDING', 'SENT', 'DELIVERED')
        """,
    )
    suspend fun segmentsBetween(from: Long, to: Long): Int

    @Query(
        """
        SELECT COALESCE(SUM(segments), 0) FROM send_log
        WHERE createdAt >= :from AND createdAt < :to
          AND status IN ('SENDING', 'SENT', 'DELIVERED')
        """,
    )
    fun observeSegmentsBetween(from: Long, to: Long): Flow<Int>

    // --- Sync -------------------------------------------------------------

    @Query("SELECT * FROM campaigns WHERE synced = 0 AND status IN ('COMPLETED', 'ABORTED') ORDER BY createdAt")
    suspend fun unsynced(): List<CampaignEntity>

    @Query("SELECT COUNT(*) FROM campaigns WHERE synced = 0 AND status IN ('COMPLETED', 'ABORTED')")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("UPDATE campaigns SET synced = 1, syncedAt = :at WHERE id = :id")
    suspend fun markSynced(id: String, at: Long)

    /**
     * Traegt die Fehlerzahl nach, sobald das Modem sich meldet.
     *
     * Der Worker ist mit dem Absetzen fertig, lange bevor die SENT-Quittungen
     * eintreffen - beim Absetzen weiss niemand, ob eine Nachricht ankommt.
     * Ohne dieses Nachtragen bleibt in der Kampagne "0 fehlgeschlagen"
     * stehen, obwohl in send_log jeder einzelne Empfaenger auf FAILED steht.
     * Genau dieser Fall ist am Geraet aufgefallen: Uebersicht gruen,
     * Detailansicht rot.
     *
     * Aendert sich die Zahl, wird synced zurueckgesetzt. Der Server hat dann
     * eine falsche Zahl und muss sie noch einmal bekommen - sonst waere der
     * Alarm "failed > 0" wirkungslos, weil der erste Upload immer 0 meldet.
     */
    @Query(
        """
        UPDATE campaigns
        SET failedCount = :failed,
            synced = CASE WHEN failedCount != :failed THEN 0 ELSE synced END
        WHERE id = :id
        """,
    )
    suspend fun updateFailedCount(id: String, failed: Int)

    @Query("SELECT failedCount FROM campaigns WHERE id = :id")
    suspend fun failedCountOf(id: String): Int?
}

@Dao
interface SendLogDao {

    /**
     * Reserviert einen Empfaenger fuer den Versand.
     *
     * Rueckgabe -1 bedeutet: der UNIQUE-Index (campaignId, msisdn) hat
     * gegriffen, diese Nummer wurde in dieser Kampagne bereits bedient und
     * ist zu ueberspringen. Genau das ist der Mechanismus gegen
     * Doppelversand - der Rueckgabewert MUSS im Worker ausgewertet werden.
     *
     * IGNORE statt ABORT, damit die Sperre ohne Exception auskommt und auch
     * innerhalb einer laufenden Transaktion sauber bleibt.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun reserve(entry: SendLogEntity): Long

    @Query("SELECT * FROM send_log WHERE campaignId = :campaignId AND msisdn = :msisdn")
    suspend fun find(campaignId: String, msisdn: String): SendLogEntity?

    @Query("SELECT * FROM send_log WHERE campaignId = :campaignId ORDER BY id")
    suspend fun forCampaign(campaignId: String): List<SendLogEntity>

    @Query("SELECT COUNT(*) FROM send_log WHERE campaignId = :campaignId AND status = 'FAILED'")
    suspend fun failedCount(campaignId: String): Int

    @Query(
        """
        UPDATE send_log
        SET partsSent = partsSent + 1,
            status = CASE WHEN status = 'SENDING' THEN 'SENT' ELSE status END,
            updatedAt = :now
        WHERE campaignId = :campaignId AND msisdn = :msisdn
        """,
    )
    suspend fun recordPartSent(campaignId: String, msisdn: String, now: Long)

    @Query(
        """
        UPDATE send_log
        SET partsDelivered = partsDelivered + 1,
            status = CASE WHEN partsDelivered + 1 >= partsTotal AND status != 'FAILED'
                          THEN 'DELIVERED' ELSE status END,
            updatedAt = :now
        WHERE campaignId = :campaignId AND msisdn = :msisdn
        """,
    )
    suspend fun recordPartDelivered(campaignId: String, msisdn: String, now: Long)

    @Query(
        """
        UPDATE send_log
        SET partsFailed = partsFailed + 1, status = 'FAILED', errorCode = :errorCode, updatedAt = :now
        WHERE campaignId = :campaignId AND msisdn = :msisdn
        """,
    )
    suspend fun recordPartFailed(campaignId: String, msisdn: String, errorCode: Int, now: Long)

    @Query("UPDATE send_log SET status = :status, errorCode = :errorCode, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: SendStatus, errorCode: Int?, now: Long)

    /**
     * Gibt einen fehlgeschlagenen Empfaenger fuer genau einen manuellen
     * Neuversuch frei.
     *
     * Die Bedingung auf status = 'FAILED' und den erwarteten attempt-Wert macht
     * das Ganze atomar: zwei parallele Aufrufe koennen nicht beide 1
     * zurueckliefern. Liefert die Methode 0, hat ein anderer Aufruf den
     * Neuversuch bereits uebernommen und der eigene bricht ab.
     */
    @Query(
        """
        UPDATE send_log
        SET status = 'SENDING', attempt = attempt + 1, partsSent = 0, partsDelivered = 0,
            partsFailed = 0, errorCode = NULL, createdAt = :now, updatedAt = :now
        WHERE campaignId = :campaignId AND msisdn = :msisdn
          AND status = 'FAILED' AND attempt = :expectedAttempt
        """,
    )
    suspend fun claimForManualRetry(
        campaignId: String,
        msisdn: String,
        expectedAttempt: Int,
        now: Long,
    ): Int

}
