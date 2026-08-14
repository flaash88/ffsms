package cc.netwokx.ffsms.update

import android.content.Context
import cc.netwokx.ffsms.BuildConfig
import cc.netwokx.ffsms.app.ServiceLocator
import cc.netwokx.ffsms.data.remote.ApiClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fragt beim Backend nach, ob eine neuere Version bereitliegt, und
 * installiert sie auf Wunsch.
 *
 * Verglichen wird ausschliesslich der versionCode. Ist er nicht groesser als
 * der eigene, passiert nichts - ein Downgrade wuerde Android ohnehin
 * ablehnen, und ein erneutes Installieren derselben Version waere nur
 * laestig.
 */
class UpdateChecker(private val context: Context) {

    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        val settings = ServiceLocator.settings(context).current()
        if (!settings.backendConfigured) {
            return@withContext UpdateResult.Failed("Backend nicht eingerichtet")
        }

        try {
            val api = ApiClientFactory.create(settings.backendUrl, settings.apiKey)
            val manifest = api.updateManifest()

            if (manifest.versionCode <= BuildConfig.VERSION_CODE) {
                UpdateResult.UpToDate
            } else {
                UpdateResult.Available(
                    AvailableUpdate(
                        versionCode = manifest.versionCode,
                        versionName = manifest.versionName,
                        sizeBytes = manifest.sizeBytes,
                        sha256 = manifest.sha256,
                        notes = manifest.notes,
                    ),
                )
            }
        } catch (e: Exception) {
            UpdateResult.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    /**
     * Laedt das APK und uebergibt es dem System.
     * @return null bei Erfolg, sonst der Fehlergrund.
     */
    suspend fun download(update: AvailableUpdate): String? = withContext(Dispatchers.IO) {
        val settings = ServiceLocator.settings(context).current()
        try {
            val api = ApiClientFactory.create(settings.backendUrl, settings.apiKey)
            api.updateApk().byteStream().use { stream ->
                UpdateInstaller(context).installFrom(
                    input = stream,
                    expectedSha256 = update.sha256,
                    expectedSize = update.sizeBytes,
                )
            }
        } catch (e: Exception) {
            e.message ?: e::class.java.simpleName
        }
    }
}
