package cc.netwokx.ffsms.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import cc.netwokx.ffsms.R
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
                Notifications.updateFailed(context, explain(context, status, message))
            }
        }
    }

    /**
     * Uebersetzt die Meldung des Systems in etwas Handlungsfaehiges.
     *
     * Die Rohmeldungen sind fuer den Betreiber eines Feuerwehrhandys nicht zu
     * gebrauchen: "INSTALL_FAILED_VERIFICATION_FAILURE" klingt nach einem
     * Problem mit der Signatur, kommt aber von Google Play Protect und hat mit
     * dem Keystore nichts zu tun. Wer das nicht weiss, sucht an der voellig
     * falschen Stelle - naemlich beim Keystore, dem einzigen Teil des Systems,
     * der sich nicht gefahrlos anfassen laesst.
     */
    private fun explain(context: Context, status: Int, raw: String?): String = when {
        raw?.contains("VERIFICATION_FAILURE", ignoreCase = true) == true ->
            context.getString(R.string.install_error_verification)

        raw?.contains("UPDATE_INCOMPATIBLE", ignoreCase = true) == true ||
            raw?.contains("INCONSISTENT_CERTIFICATES", ignoreCase = true) == true ->
            context.getString(R.string.install_error_signature)

        raw?.contains("INSUFFICIENT_STORAGE", ignoreCase = true) == true ->
            context.getString(R.string.install_error_storage)

        status == PackageInstaller.STATUS_FAILURE_ABORTED ->
            context.getString(R.string.install_error_aborted)

        else -> raw ?: context.getString(R.string.install_error_generic, status)
    }
}
