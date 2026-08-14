package cc.netwokx.ffsms.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import cc.netwokx.ffsms.notify.Notifications

/**
 * Nimmt das Ergebnis einer Installationssitzung entgegen.
 *
 * Der wichtige Fall ist STATUS_PENDING_USER_ACTION: Android hat das APK
 * angenommen und will jetzt die Bestaetigung des Benutzers. Der dafuer
 * mitgelieferte Intent muss gestartet werden, sonst passiert nichts weiter
 * und das Update bleibt liegen.
 */
class InstallStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { confirm?.let(context::startActivity) }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Notifications.updateInstalled(context)
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Notifications.updateFailed(context, message ?: "Fehlercode $status")
            }
        }
    }
}
