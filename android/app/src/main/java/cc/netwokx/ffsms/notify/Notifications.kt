package cc.netwokx.ffsms.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import cc.netwokx.ffsms.R
import cc.netwokx.ffsms.send.AbortReason

/**
 * Benachrichtigungen der App.
 *
 * Der Versand laeuft als Vordergrunddienst - sichtbar und abbrechbar durch den
 * Benutzer. Ein SMS-Versand, der unsichtbar im Hintergrund laeuft, ist bei
 * diesem Kostenrisiko die falsche Bauweise.
 */
object Notifications {

    private const val CHANNEL_SENDING = "sending"
    private const val CHANNEL_RESULT = "result"

    private const val ID_SENDING = 1001
    private const val ID_RESULT = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SENDING,
                context.getString(R.string.notif_channel_sending),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notif_channel_sending_desc) },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULT,
                context.getString(R.string.notif_channel_result),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.notif_channel_result_desc) },
        )
    }

    fun sendingForegroundInfo(context: Context): ForegroundInfo {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_SENDING)
            .setContentTitle(context.getString(R.string.notif_sending_title))
            .setContentText(context.getString(R.string.notif_sending_text))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(ID_SENDING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ID_SENDING, notification)
        }
    }

    fun campaignFinished(context: Context, dispatched: Int, skipped: Int, failed: Int) {
        val text = buildString {
            append(context.getString(R.string.notif_done_dispatched, dispatched))
            if (skipped > 0) append(context.getString(R.string.notif_done_skipped, skipped))
            if (failed > 0) append(context.getString(R.string.notif_done_failed, failed))
        }
        notify(
            context,
            title = context.getString(R.string.notif_done_title),
            text = text,
            high = failed > 0,
        )
    }

    fun campaignAborted(context: Context, reason: AbortReason, dispatchedSoFar: Int) {
        notify(
            context,
            title = context.getString(R.string.notif_aborted_title),
            text = context.getString(R.string.notif_aborted_text, reason.message, dispatchedSoFar),
            high = true,
        )
    }

    fun updateAvailable(context: Context, update: cc.netwokx.ffsms.update.AvailableUpdate) {
        val text = buildString {
            append(context.getString(R.string.notif_update_text, update.versionName))
            update.notes?.takeIf { it.isNotBlank() }?.let { append("\n\n").append(it) }
        }
        notify(
            context,
            title = context.getString(R.string.notif_update_title),
            text = text,
            high = false,
        )
    }

    fun updateInstalled(context: Context) = notify(
        context,
        title = context.getString(R.string.notif_update_done_title),
        text = context.getString(R.string.notif_update_done_text),
        high = false,
    )

    fun updateFailed(context: Context, reason: String) = notify(
        context,
        title = context.getString(R.string.notif_update_failed_title),
        text = context.getString(R.string.notif_update_failed_text, reason),
        high = true,
    )

    fun contactSync(context: Context, added: Int, missing: Int, skipped: Int) {
        val text = buildString {
            if (added > 0) append(context.getString(R.string.notif_contactsync_added, added))
            if (missing > 0) {
                if (isNotEmpty()) append("\n")
                append(context.getString(R.string.notif_contactsync_missing, missing))
            }
            if (skipped > 0) {
                if (isNotEmpty()) append("\n")
                append(context.getString(R.string.notif_contactsync_skipped, skipped))
            }
        }
        notify(
            context,
            title = context.getString(R.string.notif_contactsync_title),
            text = text,
            // Fehlende Empfaenger sind wichtig genug, um aufzufallen.
            high = missing > 0 || skipped > 0,
        )
    }

    private fun notify(context: Context, title: String, text: String, high: Boolean) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, if (high) CHANNEL_RESULT else CHANNEL_SENDING)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .setPriority(if (high) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Ohne POST_NOTIFICATIONS wirft notify() eine SecurityException. Der
        // Versand selbst darf daran nicht scheitern.
        runCatching {
            NotificationManagerCompat.from(context).notify(ID_RESULT, notification)
        }
    }
}
