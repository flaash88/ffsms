package cc.netwokx.ffsms.send

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import cc.netwokx.ffsms.app.ServiceLocator
import cc.netwokx.ffsms.data.db.CampaignEntity
import cc.netwokx.ffsms.data.db.CampaignStatus
import cc.netwokx.ffsms.data.db.SendLogEntity
import cc.netwokx.ffsms.data.db.SendStatus
import cc.netwokx.ffsms.domain.sms.SegmentCalculator
import cc.netwokx.ffsms.domain.time.TimeRanges
import cc.netwokx.ffsms.notify.Notifications
import cc.netwokx.ffsms.sync.SyncScheduler
import kotlinx.coroutines.delay

/**
 * Versendet eine Kampagne.
 *
 * Gesendet wird ausschliesslich aus einem Worker heraus - kein ViewModel,
 * keine Activity, kein Lifecycle-Scope setzt selbst eine SMS ab. Deshalb
 * ueberstehen Bildschirmdrehung, App-Kill und Doppeltipp den Versand, ohne
 * ihn erneut auszuloesen.
 *
 * Drei unabhaengige Sperren gegen Mehrfachversand:
 *
 *  1. `enqueueUniqueWork(campaignId, KEEP, ...)` - eine zweite Einreihung
 *     derselben Kampagne wird vom WorkManager verworfen. Die Campaign-UUID
 *     entsteht beim OEFFNEN des Bestaetigungsdialogs, ein Doppeltipp auf
 *     "Senden" erzeugt also dieselbe UUID.
 *  2. Der UNIQUE-Index (campaignId, msisdn) in send_log. Vor JEDEM Versand
 *     wird die Zeile eingefuegt; kommt -1 zurueck, wurde die Nummer bereits
 *     bedient und wird uebersprungen. Das haelt auch nach einem Prozesstod
 *     mitten im Versand.
 *  3. Die Obergrenzen je Kampagne und je Tag.
 *
 * Was hier bewusst FEHLT: ein automatischer Retry. Der Worker liefert nie
 * Result.retry(). Ein automatischer Retry, der nach einem Teilversand von
 * vorne beginnt, ist die wahrscheinlichste Ursache des 2100er-Vorfalls.
 * Fehlgeschlagene Empfaenger werden markiert und muessen einzeln von Hand
 * neu angestossen werden.
 */
class SmsSendWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_CAMPAIGN_ID = "campaign_id"

        /** Pause zwischen zwei Empfaengern. Entlastet Modem und Netz. */
        const val THROTTLE_MS = 800L

        fun uniqueWorkName(campaignId: String): String = "send:$campaignId"
    }

    private val db = ServiceLocator.database(appContext)
    private val campaignDao = db.campaignDao()
    private val sendLogDao = db.sendLogDao()
    private val sender = SingleSender(appContext)

    override suspend fun getForegroundInfo(): ForegroundInfo =
        Notifications.sendingForegroundInfo(applicationContext)

    override suspend fun doWork(): Result {
        val campaignId = inputData.getString(KEY_CAMPAIGN_ID) ?: return Result.failure()
        val campaign = campaignDao.findById(campaignId) ?: return Result.failure()

        // Bereits abgeschlossen? Dann war das ein erneuter Anlauf des
        // WorkManagers nach einem Prozesstod - nichts mehr zu tun.
        if (campaign.status == CampaignStatus.COMPLETED || campaign.status == CampaignStatus.ABORTED) {
            return Result.success()
        }

        runCatching { setForeground(getForegroundInfo()) }

        campaignDao.setStatus(campaignId, CampaignStatus.RUNNING)

        preflightAbort(campaign)?.let { return abort(campaign, it) }

        val recipients = campaignDao.recipientsFor(campaignId)
        if (recipients.isEmpty()) return abort(campaign, AbortReason.NO_RECIPIENTS)

        val smsManager = sender.smsManager() ?: return abort(campaign, AbortReason.SMS_UNAVAILABLE)

        // Segmente aus der eigenen Berechnung - dieselbe Zahl, die im
        // Bestaetigungsdialog stand. parts stammt aus dem System und dient
        // nur der Aufteilung beim Absetzen.
        val segmentsPerRecipient = SegmentCalculator.calculate(campaign.text).segments
        val parts = sender.divide(campaign.text)

        var dispatched = 0
        var skipped = 0

        for (recipient in recipients) {
            // Tageslimit bei JEDEM Empfaenger neu pruefen, nicht nur einmal
            // vorab: eine zweite Kampagne kann parallel gelaufen sein.
            if (dailySegmentsUsed() + segmentsPerRecipient > limits().maxSegmentsPerDay) {
                return abort(campaign, AbortReason.LIMIT_DAILY, dispatchedSoFar = dispatched)
            }

            val now = System.currentTimeMillis()
            val reserved = sendLogDao.reserve(
                SendLogEntity(
                    campaignId = campaignId,
                    msisdn = recipient.msisdn,
                    createdAt = now,
                    segments = segmentsPerRecipient,
                    status = SendStatus.SENDING,
                    partsTotal = parts.size,
                ),
            )

            if (reserved == -1L) {
                // Sperre hat gegriffen: diese Nummer wurde in dieser Kampagne
                // bereits bedient. Nicht erneut senden.
                skipped++
                continue
            }

            val accepted = sender.dispatch(smsManager, campaignId, recipient.msisdn, parts)
            if (!accepted) {
                sendLogDao.setStatus(
                    id = reserved,
                    status = SendStatus.FAILED,
                    errorCode = null,
                    now = System.currentTimeMillis(),
                )
            }
            dispatched++

            if (dispatched < recipients.size) delay(THROTTLE_MS)
        }

        val failed = sendLogDao.failedCount(campaignId)
        campaignDao.finish(
            id = campaignId,
            status = CampaignStatus.COMPLETED,
            failed = failed,
            reason = null,
            finishedAt = System.currentTimeMillis(),
        )

        Notifications.campaignFinished(applicationContext, dispatched, skipped, failed)
        SyncScheduler.requestSyncNow(applicationContext)
        return Result.success()
    }

    /**
     * Pruefungen, die VOR dem ersten Versand greifen muessen.
     * Reihenfolge ist relevant: ohne Berechtigung braucht das Limit nicht
     * geprueft zu werden.
     */
    private suspend fun preflightAbort(campaign: CampaignEntity): AbortReason? {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return AbortReason.NO_PERMISSION
        }

        val limits = limits()
        if (campaign.totalSegments > limits.maxSegmentsPerCampaign) return AbortReason.LIMIT_CAMPAIGN
        if (dailySegmentsUsed() + campaign.totalSegments > limits.maxSegmentsPerDay) {
            return AbortReason.LIMIT_DAILY
        }
        return null
    }

    private suspend fun limits() = ServiceLocator.settings(applicationContext).current()

    private suspend fun dailySegmentsUsed(): Int {
        val today = TimeRanges.today()
        return campaignDao.segmentsBetween(today.from, today.to)
    }

    private suspend fun abort(
        campaign: CampaignEntity,
        reason: AbortReason,
        dispatchedSoFar: Int = 0,
    ): Result {
        campaignDao.finish(
            id = campaign.id,
            status = CampaignStatus.ABORTED,
            failed = sendLogDao.failedCount(campaign.id),
            reason = reason.code,
            finishedAt = System.currentTimeMillis(),
        )
        Notifications.campaignAborted(applicationContext, reason, dispatchedSoFar)
        // Auch ein Abbruch geht ans Backend - gerade der ist die interessante
        // Information fuer den Sofort-Alarm.
        SyncScheduler.requestSyncNow(applicationContext)

        // success(), nicht failure(): der Abbruch ist ein geordnetes Ergebnis.
        // failure() wuerde nichts verbessern, retry() waere gefaehrlich.
        return Result.success()
    }
}
