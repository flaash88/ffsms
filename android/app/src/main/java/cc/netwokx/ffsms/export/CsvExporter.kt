package cc.netwokx.ffsms.export

import cc.netwokx.ffsms.data.db.CampaignEntity
import cc.netwokx.ffsms.send.AbortReason
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Erzeugt den CSV-Export des Verlaufs.
 *
 * Zweck ist der Abgleich mit dem Einzelverbindungsnachweis des Providers:
 * Datum und Uhrzeit getrennt, Segmente je Aussendung und Gesamtsumme, damit
 * sich die Rechnungsposition Zeile fuer Zeile nachvollziehen laesst.
 *
 * Semikolon als Trennzeichen und CRLF als Zeilenende, weil die Datei in aller
 * Regel in einem deutschsprachigen Excel geoeffnet wird. Das BOM sorgt dafuer,
 * dass Umlaute dort nicht zerfallen.
 */
object CsvExporter {

    private const val SEPARATOR = ";"
    private const val NEWLINE = "\r\n"
    private val BOM = Char(0xFEFF).toString()

    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val HEADER = listOf(
        "Datum",
        "Uhrzeit",
        "Gruppe",
        "Empfaenger",
        "Segmente je Nachricht",
        "Segmente gesamt",
        "Encoding",
        "Status",
        "Fehlgeschlagen",
        "Abbruchgrund",
        "Kampagne",
    )

    fun export(
        campaigns: List<CampaignEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        append(BOM)
        append(HEADER.joinToString(SEPARATOR))
        append(NEWLINE)

        for (c in campaigns.sortedBy { it.createdAt }) {
            val at = Instant.ofEpochMilli(c.createdAt).atZone(zone)
            val row = listOf(
                DATE.format(at),
                TIME.format(at),
                c.groupName,
                c.recipientCount.toString(),
                c.segmentsPerMessage.toString(),
                c.totalSegments.toString(),
                c.encoding,
                statusLabel(c),
                c.failedCount.toString(),
                AbortReason.fromCode(c.abortedReason)?.message ?: c.abortedReason.orEmpty(),
                c.id,
            )
            append(row.joinToString(SEPARATOR) { escape(it) })
            append(NEWLINE)
        }

        // Summenzeile: die Zahl, die gegen die Providerrechnung gehalten wird.
        val total = campaigns.sumOf { it.totalSegments }
        append(NEWLINE)
        append(escape("Summe Segmente"))
        append(SEPARATOR.repeat(4))
        append(total.toString())
        append(NEWLINE)
    }

    fun suggestedFileName(zone: ZoneId = ZoneId.systemDefault()): String {
        val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Instant.now().atZone(zone))
        return "ff-sms-verlauf-$stamp.csv"
    }

    private fun statusLabel(c: CampaignEntity) = when (c.status.name) {
        "COMPLETED" -> "abgeschlossen"
        "ABORTED" -> "abgebrochen"
        "RUNNING" -> "laeuft"
        else -> "eingereiht"
    }

    /** RFC-4180-Quoting, damit ein Semikolon im Gruppennamen die Spalten nicht verschiebt. */
    private fun escape(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
