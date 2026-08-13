package cc.netwokx.ffsms.send

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager

/**
 * Kapselt den eigentlichen Aufruf des [SmsManager].
 *
 * Bewusst als einzige Stelle im Projekt, die sendet - sowohl der
 * Kampagnen-Worker als auch der manuelle Einzel-Neuversuch gehen hier durch.
 * Wer nach "wo wird gesendet" sucht, findet genau diese Datei.
 */
class SingleSender(private val context: Context) {

    fun smsManager(): SmsManager? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    } catch (e: Exception) {
        null
    }

    fun divide(text: String): ArrayList<String> =
        smsManager()?.let { ArrayList(it.divideMessage(text)) } ?: arrayListOf(text)

    /** Bequemlichkeitsvariante fuer den Einzelversand. */
    fun send(campaignId: String, msisdn: String, text: String): Boolean {
        val manager = smsManager() ?: return false
        return dispatch(manager, campaignId, msisdn, divide(text))
    }

    /**
     * Setzt eine Nachricht ab.
     * @return false, wenn schon das Absetzen fehlschlaegt. Das eigentliche
     *         Ergebnis kommt asynchron ueber [SmsStatusReceiver].
     */
    fun dispatch(
        manager: SmsManager,
        campaignId: String,
        msisdn: String,
        parts: ArrayList<String>,
    ): Boolean = try {
        val sentIntents = ArrayList<PendingIntent>(parts.size)
        val deliveredIntents = ArrayList<PendingIntent>(parts.size)
        for (index in parts.indices) {
            sentIntents += statusIntent(SmsStatusReceiver.ACTION_SENT, campaignId, msisdn, index)
            deliveredIntents += statusIntent(SmsStatusReceiver.ACTION_DELIVERED, campaignId, msisdn, index)
        }
        manager.sendMultipartTextMessage(msisdn, null, parts, sentIntents, deliveredIntents)
        true
    } catch (e: Exception) {
        // IllegalArgumentException (unbrauchbare Nummer), SecurityException
        // (Berechtigung waehrend des Laufs entzogen) und aehnliches. Der
        // Empfaenger wird als fehlgeschlagen markiert, der Lauf geht weiter.
        false
    }

    /**
     * PendingIntent fuer eine Statusrueckmeldung.
     *
     * Die Uri macht jeden Intent eindeutig. Ohne sie wuerde
     * FLAG_UPDATE_CURRENT die Extras aller Empfaenger auf den zuletzt
     * erzeugten ueberschreiben - die Statusmeldungen landeten dann beim
     * falschen Kontakt.
     */
    private fun statusIntent(
        action: String,
        campaignId: String,
        msisdn: String,
        partIndex: Int,
    ): PendingIntent {
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("ffsms://$campaignId/$msisdn/$partIndex/$action")
            putExtra(SmsStatusReceiver.EXTRA_CAMPAIGN_ID, campaignId)
            putExtra(SmsStatusReceiver.EXTRA_MSISDN, msisdn)
            putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, partIndex)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
