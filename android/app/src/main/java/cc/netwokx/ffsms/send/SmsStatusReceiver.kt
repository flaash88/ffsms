package cc.netwokx.ffsms.send

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import cc.netwokx.ffsms.app.ServiceLocator
import cc.netwokx.ffsms.notify.Notifications
import cc.netwokx.ffsms.sync.SyncScheduler
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
        val app = context.applicationContext
        val db = ServiceLocator.database(app)
        val dao = db.sendLogDao()
        val campaigns = db.campaignDao()

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

                // Die Fehlerzahl der Kampagne nachtragen. Der Worker war schon
                // fertig, als diese Quittung unterwegs war - ohne das bliebe
                // in der Uebersicht "abgeschlossen" ohne jeden Hinweis stehen.
                val before = campaigns.failedCountOf(campaignId) ?: 0
                val failed = dao.failedCount(campaignId)
                if (failed != before) {
                    campaigns.updateFailedCount(campaignId, failed)
                    // Beim ersten Fehlschlag einmal melden. Wer die App
                    // weggelegt hat, erfaehrt sonst nie davon.
                    if (before == 0) {
                        Notifications.campaignHasFailures(app, failed)
                    }
                    SyncScheduler.requestSyncNow(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Klartext fuer die Fehlercodes des SmsManagers.
 *
 * Die Zahlen stehen hier als Literale und nicht als SmsManager-Konstanten:
 * die meisten davon gibt es erst ab API 30, die App laeuft ab API 26. Ein
 * roher "Fehlercode 32" hilft am Feuerwehrhandy niemandem weiter - und
 * ausgerechnet 32 heisst "keine Standard-SMS-App", was auf einem Geraet ohne
 * SIM-Karte auftritt und wie ein Fehler der App aussieht, ohne einer zu sein.
 */
fun smsErrorText(code: Int?): String = when (code) {
    null -> "Unbekannter Fehler"
    1 -> "Allgemeiner Fehler"
    2 -> "Funkmodul aus (Flugmodus?)"
    3 -> "Leere Nachricht (PDU)"
    4 -> "Kein Netz"
    5 -> "Sendelimit des Geraets erreicht"
    6 -> "Durch die SIM gesperrte Nummer (FDN)"
    7, 8 -> "Kurzwahl nicht erlaubt"
    9 -> "Funkmodul nicht verfuegbar"
    10 -> "Vom Netz abgewiesen"
    11 -> "Ungueltige Nummer"
    12 -> "Geraet nicht sendebereit"
    13 -> "Kein Speicher auf der SIM"
    14 -> "Ungueltiges SMS-Format"
    15 -> "Systemfehler"
    16 -> "Fehler im Funkmodul"
    17 -> "Netzfehler"
    18 -> "Zeichen nicht kodierbar"
    19 -> "SMS-Zentrale nicht eingerichtet"
    20 -> "Vom Anbieter nicht erlaubt"
    21 -> "Interner Fehler"
    22 -> "Keine freien Ressourcen"
    23 -> "Abgebrochen"
    24 -> "Vom Geraet nicht unterstuetzt"
    29 -> "Waehrend eines Notrufs gesperrt"
    30 -> "Auch nach Wiederholung des Netzes nicht zustellbar"
    31 -> "Fehler im Telefondienst"
    32 -> "Keine SIM-Karte bzw. keine Standard-SMS-App"
    else -> "Fehlercode $code"
}
