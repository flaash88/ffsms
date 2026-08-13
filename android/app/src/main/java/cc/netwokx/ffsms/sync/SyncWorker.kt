package cc.netwokx.ffsms.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cc.netwokx.ffsms.app.ServiceLocator
import cc.netwokx.ffsms.data.db.CampaignEntity
import cc.netwokx.ffsms.data.remote.ApiClientFactory
import cc.netwokx.ffsms.data.remote.CampaignUploadDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Laedt abgeschlossene Aussendungen als reine Zahlen zum Backend hoch.
 *
 * Das SMS-Handy darf offline sein: die Eintraege stehen in Room und warten,
 * bis wieder Netz da ist. Der Versand haengt niemals am Backend.
 *
 * Uebertragen wird ausschliesslich [CampaignUploadDto] - Zahlen, Zeitstempel,
 * Kodierung, Geraete-Kennung. Kein Text, keine Nummern, keine Namen.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = ServiceLocator.settings(applicationContext).current()
        if (!settings.syncEnabled || !settings.backendConfigured) return Result.success()

        val dao = ServiceLocator.database(applicationContext).campaignDao()
        val pending = dao.unsynced()
        if (pending.isEmpty()) return Result.success()

        val api = ApiClientFactory.create(settings.backendUrl, settings.apiKey)
        var hadNetworkError = false

        for (campaign in pending) {
            try {
                api.uploadCampaign(campaign.toUploadDto(settings.deviceId))
                dao.markSynced(campaign.id, System.currentTimeMillis())
            } catch (e: Exception) {
                // Hier ist ein Retry ungefaehrlich - es werden Zahlen
                // hochgeladen, keine SMS gesendet. Der Server macht ein
                // Upsert auf die campaign_id, ein doppelter Upload ist
                // folgenlos.
                hadNetworkError = true
            }
        }

        return if (hadNetworkError) Result.retry() else Result.success()
    }
}

/**
 * Bildet eine lokale Kampagne auf das Upload-Format ab.
 *
 * Diese Funktion ist die Grenze zwischen "bleibt auf dem Geraet" und "geht
 * ins Netz". campaign.text wird hier bewusst NICHT gelesen.
 */
private fun CampaignEntity.toUploadDto(deviceId: String) = CampaignUploadDto(
    deviceId = deviceId,
    campaignId = id,
    sentAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()),
    ),
    recipients = recipientCount,
    segmentsPerMsg = segmentsPerMessage,
    totalSegments = totalSegments,
    encoding = encoding,
    failed = failedCount,
    abortedReason = abortedReason,
)

object SyncScheduler {

    private const val PERIODIC_NAME = "sync-periodic"
    private const val ONE_SHOT_NAME = "sync-now"

    /** Periodischer Upload alle 30 Minuten, nur wenn Netz vorhanden ist. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Sofortiger Versuch, etwa direkt nach einer Aussendung. */
    fun requestSyncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_SHOT_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
