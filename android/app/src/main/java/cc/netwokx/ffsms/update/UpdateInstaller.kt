package cc.netwokx.ffsms.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.InputStream
import java.security.MessageDigest

/**
 * Ergebnis eines Update-Versuchs.
 */
sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(val manifest: AvailableUpdate) : UpdateResult
    data class Failed(val reason: String) : UpdateResult
}

data class AvailableUpdate(
    val versionCode: Int,
    val versionName: String,
    val sizeBytes: Long,
    val sha256: String,
    val notes: String?,
)

/**
 * Schreibt ein heruntergeladenes APK in eine PackageInstaller-Sitzung und
 * uebergibt es dem System.
 *
 * Android zeigt danach IMMER seinen eigenen Installationsdialog. Eine stille
 * Installation ist nur moeglich, wenn die App Geraeteeigentuemer ist - das
 * setzt ein zurueckgesetztes, zentral verwaltetes Geraet voraus und waere fuer
 * ein einzelnes Feuerwehrhandy voellig unverhaeltnismaessig. Der Kollege muss
 * also einmal auf "Installieren" tippen; hinfahren muss aber niemand mehr.
 *
 * Die Signatur des Updates muss mit der der installierten App uebereinstimmen,
 * sonst lehnt Android ab. Deshalb muss das Geraet von Anfang an ein
 * release-signiertes APK haben.
 */
class UpdateInstaller(private val context: Context) {

    companion object {
        const val ACTION_INSTALL_STATUS = "cc.netwokx.ffsms.INSTALL_STATUS"
    }

    /**
     * Uebertraegt den Datenstrom in eine Installationssitzung und pruefe dabei
     * die Pruefsumme.
     *
     * Die Pruefung laeuft waehrend des Schreibens mit, nicht danach: so muss
     * das APK nicht zusaetzlich in den Cache geschrieben werden, und ein
     * abgebrochener Download kann gar nicht erst installiert werden.
     *
     * @return null bei Erfolg, sonst der Fehlergrund im Klartext.
     */
    fun installFrom(
        input: InputStream,
        expectedSha256: String,
        expectedSize: Long,
    ): String? {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(context.packageName)
            if (expectedSize > 0) setSize(expectedSize)
        }

        val sessionId = try {
            installer.createSession(params)
        } catch (e: Exception) {
            return e.message ?: e::class.java.simpleName
        }

        // Merkt sich, ob die Sitzung an das System uebergeben wurde. Alles
        // andere - Pruefsummenfehler, Abbruch, Exception - wird im finally
        // aufgeraeumt, damit keine halben Sitzungen zurueckbleiben.
        var committed = false

        try {
            installer.openSession(sessionId).use { session ->
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L

                session.openWrite("ffsms", 0, if (expectedSize > 0) expectedSize else -1L)
                    .use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            digest.update(buffer, 0, read)
                            out.write(buffer, 0, read)
                            written += read
                        }
                        session.fsync(out)
                    }

                val actual = digest.digest().joinToString("") { "%02x".format(it) }

                // Rueckgabe VOR dem Commit: ein unvollstaendig oder falsch
                // uebertragenes APK darf dem System gar nicht erst angeboten
                // werden. Das finally verwirft die Sitzung.
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    return "Pruefsumme stimmt nicht (erwartet $expectedSha256, erhalten $actual)"
                }
                if (expectedSize > 0 && written != expectedSize) {
                    return "Unvollstaendiger Download ($written von $expectedSize Bytes)"
                }

                session.commit(statusIntent(sessionId).intentSender)
                committed = true
            }
            return null
        } catch (e: Exception) {
            return e.message ?: e::class.java.simpleName
        } finally {
            // Nach einem Commit gehoert die Sitzung dem System und darf nicht
            // mehr verworfen werden.
            if (!committed) runCatching { installer.abandonSession(sessionId) }
        }
    }

    private fun statusIntent(sessionId: Int): PendingIntent {
        val intent = Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName)
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}
