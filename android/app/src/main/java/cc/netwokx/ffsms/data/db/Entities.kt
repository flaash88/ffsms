package cc.netwokx.ffsms.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Alle Daten hier liegen ausschliesslich lokal auf dem Geraet.
 * An das Backend geht davon NICHTS ausser reinen Zahlen - siehe
 * [cc.netwokx.ffsms.data.remote.CampaignUploadDto] und den Datenschutz-Screen.
 */

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/**
 * Ein Empfaenger in einem Verteiler.
 *
 * [msisdn] ist immer normalisiert (E.164, z. B. +436641234567). Der
 * UNIQUE-Index ueber (groupId, msisdn) ist die erste Verteidigungslinie
 * gegen Doppelversand: eine Nummer kann in einer Gruppe nicht zweimal
 * stehen, egal wie oft sie importiert wird.
 */
@Entity(
    tableName = "recipients",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["groupId", "msisdn"], unique = true),
        Index(value = ["groupId"]),
    ],
)
data class RecipientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    /** Normalisierte Nummer in E.164, oder die Roheingabe wenn [valid] false ist. */
    val msisdn: String,
    /** Anzeigename aus dem Kontaktpicker oder manuell vergeben. Bleibt lokal. */
    val displayName: String?,
    /** false = Nummer liess sich nicht nach E.164 normalisieren. */
    val valid: Boolean,
    val addedAt: Long,
)

enum class CampaignStatus {
    /** Bestaetigt und eingereiht, Worker laeuft noch nicht oder noch nicht fertig. */
    QUEUED,
    RUNNING,
    COMPLETED,
    /** Durch ein Limit oder einen Fehler gestoppt - siehe abortedReason. */
    ABORTED,
}

/**
 * Eine Aussendung.
 *
 * [id] ist die Campaign-UUID. Sie entsteht beim OEFFNEN des Bestaetigungs-
 * dialogs, nicht beim Klick auf "Senden". Damit erzeugt ein Doppeltipp auf
 * den Senden-Button dieselbe UUID und laeuft in die Idempotenz-Sperre des
 * WorkManagers, statt eine zweite Kampagne anzulegen.
 */
@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey val id: String,
    val groupId: Long?,
    /** Kopie des Gruppennamens, damit der Verlauf ein Loeschen der Gruppe ueberlebt. */
    val groupName: String,
    val createdAt: Long,
    /** Nachrichtentext - bleibt lokal, wird fuer Detailansicht und Einzel-Retry gebraucht. */
    val text: String,
    val recipientCount: Int,
    val segmentsPerMessage: Int,
    /** recipientCount * segmentsPerMessage, also die erwartete Rechnungsposition. */
    val totalSegments: Int,
    val encoding: String,
    val status: CampaignStatus,
    val failedCount: Int = 0,
    val abortedReason: String? = null,
    val finishedAt: Long? = null,
    /** false = steht noch in der Upload-Warteschlange fuer das Backend. */
    val synced: Boolean = false,
    val syncedAt: Long? = null,
)

/**
 * Der geplante Empfaengerkreis einer Kampagne, eingefroren zum Zeitpunkt der
 * Bestaetigung. Aendert sich die Gruppe danach, bleibt der Verlauf korrekt.
 */
@Entity(
    tableName = "campaign_recipients",
    primaryKeys = ["campaignId", "msisdn"],
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["campaignId"])],
)
data class CampaignRecipientEntity(
    val campaignId: String,
    val msisdn: String,
    val displayName: String?,
    val position: Int,
)

/**
 * Zustand eines Sendeversuchs.
 *
 * Es gibt bewusst keinen Zustand "uebersprungen": wenn die Duplikatsperre
 * greift, entsteht gerade KEINE neue Zeile - die bestehende behaelt ihren
 * echten Status. Ein Empfaenger ohne Zeile in send_log war schlicht noch
 * nicht an der Reihe.
 */
enum class SendStatus {
    SENDING,
    SENT,
    DELIVERED,
    FAILED,
}

/**
 * Protokoll der tatsaechlichen Sendeversuche - der eigentliche Schutz gegen
 * Mehrfachversand.
 *
 * Der UNIQUE-Index ueber (campaignId, msisdn) wird BEWUSST als Sperre
 * missbraucht: der Worker fuegt die Zeile ein, BEVOR er sendet. Schlaegt das
 * Insert wegen der Constraint fehl, wurde diese Nummer in dieser Kampagne
 * bereits bedient und wird uebersprungen. Das haelt auch dann, wenn der
 * Prozess mitten im Versand abgeschossen und der Worker neu gestartet wird.
 */
@Entity(
    tableName = "send_log",
    indices = [
        Index(value = ["campaignId", "msisdn"], unique = true),
        Index(value = ["campaignId"]),
        Index(value = ["createdAt"]),
    ],
)
data class SendLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: String,
    val msisdn: String,
    val createdAt: Long,
    /** Segmente, die fuer diesen einen Empfaenger abgerechnet werden. */
    val segments: Int,
    val status: SendStatus,
    val partsTotal: Int,
    val partsSent: Int = 0,
    val partsDelivered: Int = 0,
    val partsFailed: Int = 0,
    val errorCode: Int? = null,
    /** Zaehler fuer manuelle Wiederholungen. Automatische Retries gibt es nicht. */
    val attempt: Int = 1,
    val updatedAt: Long = createdAt,
)
