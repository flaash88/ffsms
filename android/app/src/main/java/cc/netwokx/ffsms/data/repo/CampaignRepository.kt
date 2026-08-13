package cc.netwokx.ffsms.data.repo

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import cc.netwokx.ffsms.data.db.CampaignDao
import cc.netwokx.ffsms.data.db.CampaignEntity
import cc.netwokx.ffsms.data.db.CampaignRecipientEntity
import cc.netwokx.ffsms.data.db.CampaignStatus
import cc.netwokx.ffsms.data.db.RecipientStatusRow
import cc.netwokx.ffsms.data.db.SendLogDao
import cc.netwokx.ffsms.data.db.SendStatus
import cc.netwokx.ffsms.domain.sms.SegmentInfo
import cc.netwokx.ffsms.domain.time.TimeRanges
import cc.netwokx.ffsms.send.ManualRetryWorker
import cc.netwokx.ffsms.send.SmsSendWorker
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Eine vorbereitete, aber noch nicht bestaetigte Aussendung.
 *
 * Die [campaignId] wird beim OEFFNEN des Bestaetigungsdialogs erzeugt, nicht
 * beim Klick auf "Senden". Dadurch fuehrt ein Doppeltipp auf den Senden-Button
 * zu zweimal derselben UUID - und die zweite Einreihung wird vom WorkManager
 * (ExistingWorkPolicy.KEEP) verworfen, statt eine zweite Aussendung zu starten.
 */
data class PendingCampaign(
    val campaignId: String,
    val groupId: Long,
    val groupName: String,
    val text: String,
    val recipients: List<Recipient>,
    val info: SegmentInfo,
) {
    data class Recipient(val msisdn: String, val displayName: String?)

    val recipientCount: Int get() = recipients.size
    val totalSegments: Int get() = info.segments * recipients.size
}

/** Verbrauch, der ohne Backend-Zugriff aus der lokalen Datenbank kommt. */
data class UsageSummary(val week: Int, val month: Int)

class CampaignRepository(
    private val context: Context,
    private val campaignDao: CampaignDao,
    private val sendLogDao: SendLogDao,
) {

    fun observeCampaigns(): Flow<List<CampaignEntity>> = campaignDao.observeAll()

    fun observeCampaign(id: String): Flow<CampaignEntity?> = campaignDao.observeById(id)

    fun observeRecipientStatus(id: String): Flow<List<RecipientStatusRow>> =
        campaignDao.observeRecipientStatus(id)

    fun observeWeekSegments(): Flow<Int> =
        TimeRanges.thisWeek().let { campaignDao.observeSegmentsBetween(it.from, it.to) }

    fun observeMonthSegments(): Flow<Int> =
        TimeRanges.thisMonth().let { campaignDao.observeSegmentsBetween(it.from, it.to) }

    suspend fun usage(): UsageSummary {
        val week = TimeRanges.thisWeek()
        val month = TimeRanges.thisMonth()
        return UsageSummary(
            week = campaignDao.segmentsBetween(week.from, week.to),
            month = campaignDao.segmentsBetween(month.from, month.to),
        )
    }

    suspend fun segmentsToday(): Int =
        TimeRanges.today().let { campaignDao.segmentsBetween(it.from, it.to) }

    fun newCampaignId(): String = UUID.randomUUID().toString()

    /**
     * Bestaetigt eine Aussendung und reiht sie ein.
     *
     * Idempotent auf drei Ebenen:
     *  - Der Insert der Kampagne laeuft mit OnConflict IGNORE ueber die UUID.
     *  - Die Empfaengerliste hat (campaignId, msisdn) als Primaerschluessel.
     *  - enqueueUniqueWork mit KEEP verwirft eine zweite Einreihung.
     *
     * Ein zweiter Aufruf mit derselben PendingCampaign startet also nichts neu.
     */
    suspend fun confirmAndEnqueue(pending: PendingCampaign) {
        val now = System.currentTimeMillis()

        campaignDao.insert(
            CampaignEntity(
                id = pending.campaignId,
                groupId = pending.groupId,
                groupName = pending.groupName,
                createdAt = now,
                text = pending.text,
                recipientCount = pending.recipientCount,
                segmentsPerMessage = pending.info.segments,
                totalSegments = pending.totalSegments,
                encoding = pending.info.encoding.name,
                status = CampaignStatus.QUEUED,
            ),
        )

        campaignDao.insertRecipients(
            pending.recipients.mapIndexed { index, r ->
                CampaignRecipientEntity(
                    campaignId = pending.campaignId,
                    msisdn = r.msisdn,
                    displayName = r.displayName,
                    position = index,
                )
            },
        )

        val request = OneTimeWorkRequestBuilder<SmsSendWorker>()
            .setInputData(Data.Builder().putString(SmsSendWorker.KEY_CAMPAIGN_ID, pending.campaignId).build())
            // Ausdruecklich KEINE setBackoffCriteria und kein Constraint auf
            // Netz: der Worker soll genau einmal laufen und niemals automatisch
            // wiederholt werden.
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SmsSendWorker.uniqueWorkName(pending.campaignId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Stoesst einen einzelnen fehlgeschlagenen Empfaenger erneut an.
     * Nur von Hand aufrufbar, immer genau ein Empfaenger.
     */
    suspend fun retrySingle(campaignId: String, msisdn: String) {
        val entry = sendLogDao.find(campaignId, msisdn) ?: return
        if (entry.status != SendStatus.FAILED) return

        val request = OneTimeWorkRequestBuilder<ManualRetryWorker>()
            .setInputData(
                Data.Builder()
                    .putString(ManualRetryWorker.KEY_CAMPAIGN_ID, campaignId)
                    .putString(ManualRetryWorker.KEY_MSISDN, msisdn)
                    .putInt(ManualRetryWorker.KEY_EXPECTED_ATTEMPT, entry.attempt)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ManualRetryWorker.uniqueWorkName(campaignId, msisdn, entry.attempt),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    suspend fun allCampaigns(): List<CampaignEntity> = campaignDao.all()
}
