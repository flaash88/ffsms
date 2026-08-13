package cc.netwokx.ffsms.export

import cc.netwokx.ffsms.data.db.CampaignEntity
import cc.netwokx.ffsms.data.db.CampaignStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CsvExporterTest {

    private val vienna = ZoneId.of("Europe/Vienna")

    private fun campaign(
        id: String = "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
        at: ZonedDateTime = ZonedDateTime.of(2026, 8, 13, 18, 22, 0, 0, vienna),
        group: String = "Aktivmannschaft",
        recipients: Int = 15,
        segmentsPerMessage: Int = 3,
        encoding: String = "GSM7",
        status: CampaignStatus = CampaignStatus.COMPLETED,
        failed: Int = 0,
        abortedReason: String? = null,
    ) = CampaignEntity(
        id = id,
        groupId = 1,
        groupName = group,
        createdAt = at.toInstant().toEpochMilli(),
        text = "Uebung heute 19:00 Uhr",
        recipientCount = recipients,
        segmentsPerMessage = segmentsPerMessage,
        totalSegments = recipients * segmentsPerMessage,
        encoding = encoding,
        status = status,
        failedCount = failed,
        abortedReason = abortedReason,
    )

    private fun rows(csv: String) = csv.trim().lines().map { it.trim() }

    @Test
    fun `Kopfzeile und Datenzeile passen zusammen`() {
        val csv = CsvExporter.export(listOf(campaign()), vienna)
        val lines = rows(csv)

        val header = lines[0].removePrefix(Char(0xFEFF).toString()).split(";")
        val data = lines[1].split(";")
        assertEquals(header.size, data.size)
        assertEquals("Datum", header[0])
        assertEquals("13.08.2026", data[0])
        assertEquals("18:22:00", data[1])
        assertEquals("Aktivmannschaft", data[2])
        assertEquals("15", data[3])
        assertEquals("3", data[4])
        assertEquals("45", data[5])
        assertEquals("GSM7", data[6])
    }

    @Test
    fun `beginnt mit einem BOM damit Excel Umlaute richtig anzeigt`() {
        val csv = CsvExporter.export(listOf(campaign()), vienna)
        assertEquals(0xFEFF, csv[0].code)
    }

    @Test
    fun `verwendet CRLF als Zeilenende`() {
        val csv = CsvExporter.export(listOf(campaign()), vienna)
        assertTrue(csv.contains("\r\n"))
    }

    @Test
    fun `sortiert aufsteigend nach Zeitpunkt`() {
        // Damit sich die Datei Zeile fuer Zeile gegen den
        // Einzelverbindungsnachweis halten laesst.
        val spaet = campaign(id = "b", at = ZonedDateTime.of(2026, 8, 20, 9, 0, 0, 0, vienna))
        val frueh = campaign(id = "a", at = ZonedDateTime.of(2026, 8, 5, 9, 0, 0, 0, vienna))

        val lines = rows(CsvExporter.export(listOf(spaet, frueh), vienna))
        assertTrue(lines[1].startsWith("05.08.2026"))
        assertTrue(lines[2].startsWith("20.08.2026"))
    }

    @Test
    fun `enthaelt eine Summenzeile mit der Gesamtzahl der Segmente`() {
        val csv = CsvExporter.export(
            listOf(campaign(id = "a"), campaign(id = "b", recipients = 10, segmentsPerMessage = 2)),
            vienna,
        )
        // 15 x 3 = 45 plus 10 x 2 = 20
        assertTrue(csv.contains("Summe Segmente"))
        assertTrue(csv.trimEnd().endsWith("65"))
    }

    @Test
    fun `schreibt den Abbruchgrund im Klartext`() {
        val csv = CsvExporter.export(
            listOf(campaign(status = CampaignStatus.ABORTED, abortedReason = "limit_daily")),
            vienna,
        )
        assertTrue(csv.contains("abgebrochen"))
        assertTrue(csv.contains("Tagesobergrenze"))
    }

    @Test
    fun `maskiert ein Semikolon im Gruppennamen`() {
        // Ohne Quoting wuerde der Name die Spalten verschieben und der
        // Abgleich mit der Rechnung waere unbrauchbar.
        val csv = CsvExporter.export(listOf(campaign(group = "Zug 1; Zug 2")), vienna)
        val data = rows(csv)[1]
        assertTrue(data.contains("\"Zug 1; Zug 2\""))
        assertEquals(rows(csv)[0].split(";").size, data.split(";").size - 1)
    }

    @Test
    fun `kommt mit leerem Verlauf zurecht`() {
        val csv = CsvExporter.export(emptyList(), vienna)
        assertTrue(csv.contains("Datum"))
        assertTrue(csv.trimEnd().endsWith("0"))
    }
}
