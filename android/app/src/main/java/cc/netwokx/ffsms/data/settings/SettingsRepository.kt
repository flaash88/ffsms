package cc.netwokx.ffsms.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cc.netwokx.ffsms.BuildConfig
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ffsms_settings")

/**
 * Einstellungen der App.
 *
 * @param warnThresholdSegments ab dieser Gesamtzahl wird die Anzeige im
 *        Verfassen-Screen rot. Reine Warnung, blockiert nichts.
 * @param maxSegmentsPerCampaign harte Obergrenze je Aussendung. Der Worker
 *        bricht bei Ueberschreitung ab - es gibt keinen Weiter-Trotzdem-Pfad.
 * @param maxSegmentsPerDay harte Obergrenze pro Kalendertag, ueber alle
 *        Kampagnen hinweg. Das ist die Sicherung, die einen 2100er-Vorfall
 *        auch dann noch stoppt, wenn alles andere versagt hat.
 * @param pinProtected ob Serverzugang und Obergrenzen mit einer PIN gegen
 *        versehentliches Verstellen gesichert sind. Nur die Tatsache, nie
 *        die PIN selbst - die verlaesst das Repository nicht.
 */
data class AppSettings(
    val backendUrl: String,
    val apiKey: String,
    val deviceId: String,
    val syncEnabled: Boolean,
    val warnThresholdSegments: Int,
    val maxSegmentsPerCampaign: Int,
    val maxSegmentsPerDay: Int,
    val pinProtected: Boolean,
) {
    val backendConfigured: Boolean
        get() = backendUrl.isNotBlank() && apiKey.isNotBlank()

    companion object {
        const val DEFAULT_WARN_THRESHOLD = 100
        const val DEFAULT_MAX_PER_CAMPAIGN = 300
        const val DEFAULT_MAX_PER_DAY = 1000
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BACKEND_URL = stringPreferencesKey("backend_url")
        val API_KEY = stringPreferencesKey("api_key")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        val WARN_THRESHOLD = intPreferencesKey("warn_threshold")
        val MAX_PER_CAMPAIGN = intPreferencesKey("max_per_campaign")
        val MAX_PER_DAY = intPreferencesKey("max_per_day")
        val ADMIN_PIN = stringPreferencesKey("admin_pin")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            backendUrl = p[Keys.BACKEND_URL] ?: BuildConfig.DEFAULT_BACKEND_URL,
            apiKey = p[Keys.API_KEY] ?: "",
            deviceId = p[Keys.DEVICE_ID] ?: BuildConfig.DEFAULT_DEVICE_ID,
            // Sync ist standardmaessig AUS. Er wird erst aktiv, wenn der
            // Betreiber die Uebertragung bewusst einschaltet.
            syncEnabled = p[Keys.SYNC_ENABLED] ?: false,
            warnThresholdSegments = p[Keys.WARN_THRESHOLD] ?: AppSettings.DEFAULT_WARN_THRESHOLD,
            maxSegmentsPerCampaign = p[Keys.MAX_PER_CAMPAIGN] ?: AppSettings.DEFAULT_MAX_PER_CAMPAIGN,
            maxSegmentsPerDay = p[Keys.MAX_PER_DAY] ?: AppSettings.DEFAULT_MAX_PER_DAY,
            pinProtected = p[Keys.ADMIN_PIN] != null,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setBackendUrl(value: String) = put(Keys.BACKEND_URL, value.trim())

    suspend fun setApiKey(value: String) = put(Keys.API_KEY, value.trim())

    suspend fun setDeviceId(value: String) = put(Keys.DEVICE_ID, value.trim())

    suspend fun setSyncEnabled(value: Boolean) = put(Keys.SYNC_ENABLED, value)

    suspend fun setWarnThreshold(value: Int) = put(Keys.WARN_THRESHOLD, value.coerceAtLeast(1))

    /**
     * Die Obergrenzen lassen sich anheben, aber nicht abschalten: 0 oder
     * negative Werte werden auf 1 angehoben. Eine Aussendung ohne Limit soll
     * es in dieser App nicht geben.
     */
    suspend fun setMaxPerCampaign(value: Int) = put(Keys.MAX_PER_CAMPAIGN, value.coerceAtLeast(1))

    suspend fun setMaxPerDay(value: Int) = put(Keys.MAX_PER_DAY, value.coerceAtLeast(1))

    /**
     * PIN setzen.
     *
     * Gespeichert wird "salz:hash", nicht die PIN. Das schuetzt nicht gegen
     * jemanden, der das Geraet in der Hand hat und Root besitzt - es soll
     * verhindern, dass die PIN in einem Backup oder in einem Datenauszug
     * einfach ablesbar danebensteht. Der Zweck der Sperre ist ohnehin ein
     * anderer: das versehentliche Verstellen im Vorbeigehen.
     */
    suspend fun setAdminPin(pin: String) {
        val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }.toHex()
        put(Keys.ADMIN_PIN, "$salt:${hash(salt, pin)}")
    }

    suspend fun clearAdminPin() {
        context.dataStore.edit { it.remove(Keys.ADMIN_PIN) }
    }

    /** Ohne gesetzte PIN ist alles offen - dann trifft jede Eingabe zu. */
    suspend fun checkAdminPin(pin: String): Boolean {
        val stored = context.dataStore.data.first()[Keys.ADMIN_PIN] ?: return true
        val salt = stored.substringBefore(':', "")
        val expected = stored.substringAfter(':', "")
        return salt.isNotEmpty() && hash(salt, pin) == expected
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$salt$pin".toByteArray())
            .toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
