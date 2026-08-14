package cc.netwokx.ffsms.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cc.netwokx.ffsms.app.ServiceLocator
import cc.netwokx.ffsms.notify.Notifications
import java.util.concurrent.TimeUnit

/**
 * Gleicht Verteiler mit ihren Kontaktgruppen ab.
 *
 * Laeuft einmal taeglich. Neue Mitglieder einer Kontaktgruppe landen dadurch
 * von allein im Verteiler - genau dafuer ist die Verknuepfung da.
 *
 * Wer nicht mehr in der Kontaktgruppe steht, wird nur markiert. Das Entfernen
 * bleibt eine bewusste Entscheidung: ein Alarmierungsverteiler, der sich still
 * verkleinert, weil ein Google-Konto abgemeldet wurde oder die Synchronisation
 * klemmte, waere der schlimmste denkbare Fehler dieser App.
 */
class ContactSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Ohne Kontaktzugriff gibt es nichts abzugleichen. Kein Fehler -
        // die App ist auch mit von Hand gepflegten Verteilern voll nutzbar.
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val results = ServiceLocator.groups(applicationContext).autoSyncAll()
        if (results.isEmpty()) return Result.success()

        val added = results.values.sumOf { it.added }
        val missing = results.values.sumOf { it.missing }
        val skipped = results.values.count { !it.ran }

        // Nur melden, wenn sich etwas geaendert hat. Eine taegliche Meldung
        // "nichts Neues" wuerde nur abstumpfen.
        if (added > 0 || missing > 0 || skipped > 0) {
            Notifications.contactSync(applicationContext, added, missing, skipped)
        }
        return Result.success()
    }
}

object ContactSyncScheduler {

    private const val PERIODIC_NAME = "contact-sync"

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<ContactSyncWorker>(1, TimeUnit.DAYS).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
