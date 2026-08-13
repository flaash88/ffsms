package cc.netwokx.ffsms.domain.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSanitizerTest {

    private fun encodingOf(s: String) = SegmentCalculator.calculate(s).encoding

    @Test
    fun `typografische Zeichen werden ersetzt und die Nachricht faellt auf GSM-7 zurueck`() {
        val input = "Der „Einsatz“ ist beendet – alles o’k …"
        assertEquals(SmsEncoding.UCS2, encodingOf(input))

        val r = TextSanitizer.sanitize(input)

        assertEquals("Der \"Einsatz\" ist beendet - alles o'k ...", r.text)
        assertEquals(SmsEncoding.GSM7, encodingOf(r.text))
        assertTrue(r.changed)
        assertTrue(r.removed.isEmpty())
    }

    @Test
    fun `Emojis werden entfernt und gemeldet`() {
        val r = TextSanitizer.sanitize("Einsatz 🚒 Kuehwiesen")
        assertEquals("Einsatz Kuehwiesen", r.text)
        assertEquals(listOf("🚒"), r.removed)
        assertEquals(SmsEncoding.GSM7, encodingOf(r.text))
    }

    @Test
    fun `Umlaute und scharfes s bleiben unangetastet`() {
        val input = "Übung Löschzug Kühwiesen, Straße frei, größer"
        val r = TextSanitizer.sanitize(input)
        assertEquals(input, r.text)
        assertFalse(r.changed)
    }

    @Test
    fun `Euro-Zeichen bleibt erhalten weil GSM-7-faehig`() {
        val r = TextSanitizer.sanitize("Kosten 100€")
        assertEquals("Kosten 100€", r.text)
        assertFalse(r.changed)
    }

    @Test
    fun `geschuetztes Leerzeichen wird zu normalem Leerzeichen`() {
        val input = "Alarm" + Char(0x00A0) + "Stufe" + Char(0x202F) + "2"
        assertEquals(SmsEncoding.UCS2, encodingOf(input))

        val r = TextSanitizer.sanitize(input)
        assertEquals("Alarm Stufe 2", r.text)
        assertEquals(SmsEncoding.GSM7, encodingOf(r.text))
    }

    @Test
    fun `Akzentbuchstaben werden nur reduziert wenn sie nicht GSM-7-faehig sind`() {
        // é steht im GSM-7-Basisalphabet (Position 0x05) und bleibt deshalb
        // erhalten. ë steht nicht drin und wird zu e reduziert.
        val r = TextSanitizer.sanitize("Café Zoë")
        assertEquals("Café Zoe", r.text)
        assertEquals(listOf("ë"), r.replaced)
        assertEquals(SmsEncoding.GSM7, encodingOf(r.text))
    }

    @Test
    fun `bereits sauberer Text bleibt unveraendert`() {
        val input = "Uebung heute 19:00 Uhr, Ruesthaus. Bitte puenktlich sein!"
        val r = TextSanitizer.sanitize(input)
        assertEquals(input, r.text)
        assertFalse(r.changed)
    }

    @Test
    fun `leerer Text bleibt leer`() {
        val r = TextSanitizer.sanitize("")
        assertEquals("", r.text)
        assertFalse(r.changed)
    }

    @Test
    fun `Windows-Zeilenenden werden vereinheitlicht`() {
        val r = TextSanitizer.sanitize("Zeile 1\r\nZeile 2")
        assertEquals("Zeile 1\nZeile 2", r.text)
    }

    @Test
    fun `Bereinigung senkt die Segmentzahl messbar`() {
        val dirty = "Sammelplatz ist das Rüsthaus – Abmarsch 19:00 – bitte pünktlich, " +
            "Ausrüstung nicht vergessen … Einsatzleiter meldet sich vor Ort"
        val before = SegmentCalculator.calculate(dirty)
        val after = SegmentCalculator.calculate(TextSanitizer.sanitize(dirty).text)

        assertEquals(SmsEncoding.UCS2, before.encoding)
        assertEquals(SmsEncoding.GSM7, after.encoding)
        assertTrue("Bereinigung muss Segmente sparen", after.segments < before.segments)
    }
}
