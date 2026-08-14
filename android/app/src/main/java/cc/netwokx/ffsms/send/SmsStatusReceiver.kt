package cc.netwokx.ffsms.send

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import cc.netwokx.ffsms.app.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Nimmt die SENT- und DELIVERED-Rueckmeldungen des Modems entgegen und
 * schreibt sie in send_log zurueck.
 *
 * Wichtig: dieser Receiver sendet NIE selbst etwas nach. Ein Fehler wird nur
 * protokolliert. Ein automatischer Neuversuch an dieser Stelle waere genau
 * der Mechanismus, der einen einzelnen Netzfehler in hunderte zusaetzliche
 * SMS verwandeln kann.
 */
class SmsStatusReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SENT = "cc.netwokx.ffsms.SMS_SENT"
        const val ACTION_DELIVERED = "cc.netwokx.ffsms.SMS_DELIVERED"

        const val EXTRA_CAMPAIGN_ID = "campaign_id"
        const val EXTRA_MSISDN = "msisdn"
        const val EXTRA_PART_INDEX = "part_index"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val campaignId = intent.getStringExtra(EXTRA_CAMPAIGN_ID) ?: return
        val msisdn = intent.getStringExtra(EXTRA_MSISDN) ?: return
        val action = intent.action ?: return
        val resultCode = resultCode

        // goAsync haelt den Receiver am Leben, bis der Datenbankschreibvorgang
        // durch ist. Ohne das wuerde der Prozess unter Umstaenden vorher beendet
        // und der Status ginge verloren.
        val pending = goAsync()
        val dao = ServiceLocator.database(context.applicationContext).sendLogDao()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                when {
                    action == ACTION_SENT && resultCode == Activity.RESULT_OK ->
                        dao.recordPartSent(campaignId, msisdn, now)

                    action == ACTION_SENT ->
                        dao.recordPartFailed(campaignId, msisdn, resultCode, now)

                    action == ACTION_DELIVERED && resultCode == Activity.RESULT_OK ->
                        dao.recordPartDelivered(campaignId, msisdn, now)

                    action == ACTION_DELIVERED ->
                        dao.recordPartFailed(campaignId, msisdn, resultCode, now)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Klartext fuer die Fehlercodes des SmsManagers. */
fun smsErrorText(code: Int?): String = when (code) {
    null -> "Unbekannter Fehler"
    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Allgemeiner Fehler"
    SmsManager.RESULT_ERROR_NO_SERVICE -> "Kein Netz"
    SmsManager.RESULT_ERROR_NULL_PDU -> "Leere Nachricht (PDU)"
    SmsManager.RESULT_ERROR_RADIO_OFF -> "Funkmodul aus (Flugmodus?)"
    SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "Sendelimit des Geraets erreicht"
    SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "Kurzwahl nicht erlaubt"
    else -> "Fehlercode $code"
}
