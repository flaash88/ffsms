package cc.netwokx.ffsms.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cc.netwokx.ffsms.notify.Notifications
import java.util.concurrent.TimeUnit

/**
 * Sieht einmal taeglich nach, ob eine neuere Version bereitliegt, und meldet
 * sie per Benachrichtigung.
 *
 * Es wird bewusst NICHT automatisch heruntergeladen und installiert:
 *
 *  - Android verlangt fuer die Installation ohnehin eine Bestaetigung, ein
 *    Download auf Vorrat spart also nichts.
 *  - Ein 20-MB-Download ueber Mobilfunk ohne Nachfrage waere unhoeflich.
 *  - Vor allem aber: dieses Geraet ist ein Alarmierungsgeraet. Eine
 *    Installation mitten in einem Einsatz waere der denkbar schlechteste
 *    Zeitpunkt. Wann aktualisiert wird, entscheidet der Mensch davor.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val checker = UpdateChecker(applicationContext)
        return when (val result = checker.check()) {
            is UpdateResult.Available -> {
                Notifications.updateAvailable(applicationContext, result.manifest)
                Result.success()
            }
            UpdateResult.UpToDate -> Result.success()
            // Kein Netz oder Backend gerade nicht erreichbar: spaeter erneut.
            // Hier ist ein Retry harmlos, es wird nur gelesen.
            is UpdateResult.Failed -> Result.retry()
        }
    }
}

object UpdateScheduler {

    private const val PERIODIC_NAME = "update-check"

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
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
}
