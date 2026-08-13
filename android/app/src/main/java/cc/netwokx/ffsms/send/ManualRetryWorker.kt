package cc.netwokx.ffsms.send

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cc.netwokx.ffsms.app.ServiceLocator
import cc.netwokx.ffsms.data.db.SendStatus

/**
 * Wiederholt den Versand an GENAU EINEN fehlgeschlagenen Empfaenger.
 *
 * Wird ausschliesslich durch einen expliziten Tastendruck in der
 * Detailansicht ausgeloest. Es gibt keinen Weg, hierueber eine ganze Gruppe
 * erneut zu bedienen - das ist Absicht.
 *
 * Der Anspruch auf den Neuversuch wird per bedingtem UPDATE geholt
 * (status = FAILED UND attempt = erwarteter Wert). Damit koennen zwei
 * parallele Anlaeufe nicht beide senden: der zweite bekommt 0 Zeilen zurueck
 * und bricht ab.
 */
class ManualRetryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_CAMPAIGN_ID = "campaign_id"
        const val KEY_MSISDN = "msisdn"
        const val KEY_EXPECTED_ATTEMPT = "expected_attempt"

        fun uniqueWorkName(campaignId: String, msisdn: String, attempt: Int): String =
            "retry:$campaignId:$msisdn:$attempt"
    }

    override suspend fun doWork(): Result {
        val campaignId = inputData.getString(KEY_CAMPAIGN_ID) ?: return Result.failure()
        val msisdn = inputData.getString(KEY_MSISDN) ?: return Result.failure()
        val expectedAttempt = inputData.getInt(KEY_EXPECTED_ATTEMPT, -1)
        if (expectedAttempt < 0) return Result.failure()

        val db = ServiceLocator.database(applicationContext)
        val campaign = db.campaignDao().findById(campaignId) ?: return Result.failure()
        val sendLogDao = db.sendLogDao()

        val claimed = sendLogDao.claimForManualRetry(
            campaignId = campaignId,
            msisdn = msisdn,
            expectedAttempt = expectedAttempt,
            now = System.currentTimeMillis(),
        )
        if (claimed == 0) {
            // Ein anderer Anlauf war schneller, oder der Eintrag ist nicht
            // mehr fehlgeschlagen. Nicht senden.
            return Result.success()
        }

        val sender = SingleSender(applicationContext)
        val ok = sender.send(campaignId, msisdn, campaign.text)
        if (!ok) {
            val entry = sendLogDao.find(campaignId, msisdn)
            if (entry != null) {
                sendLogDao.setStatus(
                    id = entry.id,
                    status = SendStatus.FAILED,
                    errorCode = null,
                    now = System.currentTimeMillis(),
                )
            }
        }
        return Result.success()
    }
}
