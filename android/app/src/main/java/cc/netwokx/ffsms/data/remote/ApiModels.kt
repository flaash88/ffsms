package cc.netwokx.ffsms.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Die EINZIGE Struktur, die dieses Geraet an das Backend sendet.
 *
 * Sie enthaelt ausschliesslich Zahlen, einen Zeitstempel, die Kodierung und
 * die Geraete-Kennung. Es gibt hier bewusst kein Feld fuer Nachrichtentext,
 * Telefonnummern, Kontaktnamen, Textfragmente oder Hashes davon - auch nicht
 * optional, auch nicht auskommentiert.
 *
 * Wer dieses Datenmodell erweitert, muss den Datenschutz-Screen
 * (ui/privacy/PrivacyScreen.kt) und den Abschnitt in der README mit aendern.
 * Das Backend weist unbekannte Felder ohnehin mit HTTP 400 zurueck
 * (forbidNonWhitelisted), sodass ein versehentlich hinzugefuegtes Feld nicht
 * still durchrutschen kann.
 */
@Serializable
data class CampaignUploadDto(
    @SerialName("device_id") val deviceId: String,
    @SerialName("campaign_id") val campaignId: String,
    /** ISO-8601 mit Zeitzonen-Offset, z. B. 2026-08-13T18:22:00+02:00 */
    @SerialName("sent_at") val sentAt: String,
    @SerialName("recipients") val recipients: Int,
    @SerialName("segments_per_msg") val segmentsPerMsg: Int,
    @SerialName("total_segments") val totalSegments: Int,
    /** "GSM7" oder "UCS2" */
    @SerialName("encoding") val encoding: String,
    @SerialName("failed") val failed: Int,
    /** Grund eines Abbruchs, z. B. "limit_campaign". null wenn regulaer beendet. */
    @SerialName("aborted_reason") val abortedReason: String? = null,
)

@Serializable
data class CampaignAckDto(
    @SerialName("campaign_id") val campaignId: String,
    /** true = Datensatz war schon vorhanden, der Upload war idempotent. */
    @SerialName("duplicate") val duplicate: Boolean = false,
)

@Serializable
data class StatsDto(
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("from") val from: String,
    @SerialName("to") val to: String,
    @SerialName("campaigns") val campaigns: Int,
    @SerialName("recipients") val recipients: Int,
    @SerialName("total_segments") val totalSegments: Int,
    @SerialName("failed") val failed: Int,
    @SerialName("aborted") val aborted: Int,
    @SerialName("by_encoding") val byEncoding: Map<String, Int> = emptyMap(),
)

@Serializable
data class HealthDto(
    @SerialName("status") val status: String,
    @SerialName("version") val version: String? = null,
)

/**
 * Beschreibung des bereitliegenden Updates.
 *
 * Enthaelt keine personenbezogenen Daten - die Abfrage geht in die andere
 * Richtung als der Verbrauchs-Upload und laedt lediglich herunter.
 */
@Serializable
data class UpdateManifestDto(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    /** Pruefsumme des APK. Wird nach dem Download erneut geprueft. */
    @SerialName("sha256") val sha256: String,
    @SerialName("notes") val notes: String? = null,
    @SerialName("released_at") val releasedAt: String? = null,
)
