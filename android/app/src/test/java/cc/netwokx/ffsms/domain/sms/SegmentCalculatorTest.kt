package cc.netwokx.ffsms.domain.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Diese Tests sind der Vertrag der Kostenanzeige. Wenn hier etwas rot wird,
 * zeigt die App dem Einsatzleiter falsche Kosten an - deshalb sind sie die
 * erste Baustelle des Projekts und nicht die letzte.
 */
class SegmentCalculatorTest {

    private fun ascii(n: Int) = "a".repeat(n)

    // --- Pflichtfaelle aus der Anforderung ---------------------------------

    @Test
    fun `160 ASCII ergibt 1 Segment`() {
        val r = SegmentCalculator.calculate(ascii(160))
        assertEquals(SmsEncoding.GSM7, r.encoding)
        assertEquals(160, r.billedUnits)
        assertEquals(1, r.segments)
        assertFalse(r.isMultipart)
    }

    @Test
    fun `161 ASCII ergibt 2 Segmente`() {
        val r = SegmentCalculator.calculate(ascii(161))
        assertEquals(SmsEncoding.GSM7, r.encoding)
        assertEquals(2, r.segments)
    }

    @Test
    fun `306 ASCII ergibt 2 Segmente`() {
        // 2 x 153 = 306, exakt voll
        assertEquals(2, SegmentCalculator.calculate(ascii(306)).segments)
    }

    @Test
    fun `307 ASCII ergibt 3 Segmente`() {
        assertEquals(3, SegmentCalculator.calculate(ascii(307)).segments)
    }

    @Test
    fun `70 Zeichen mit einem Emoji ergibt 2 Segmente`() {
        // 69 ASCII + 1 Emoji = 70 Zeichen, aber 71 UTF-16-Einheiten (Surrogate Pair)
        val text = ascii(69) + "🚒" // Feuerwehrauto
        assertEquals(70, text.codePointCount(0, text.length))
        val r = SegmentCalculator.calculate(text)
        assertEquals(SmsEncoding.UCS2, r.encoding)
        assertEquals(71, r.billedUnits)
        assertEquals(2, r.segments)
    }

    @Test
    fun `Text mit ae-Umlaut bleibt GSM-7`() {
        val r = SegmentCalculator.calculate("Übung: Löschzug Kühwiesen, Straße frei, groß")
        assertEquals(SmsEncoding.GSM7, r.encoding)
        assertTrue(r.offenders.isEmpty())
        // jedes Zeichen genau 1 Septett
        assertEquals(r.charCount, r.billedUnits)
        assertEquals(1, r.segments)
    }

    @Test
    fun `Euro-Zeichen bleibt GSM-7 zaehlt aber doppelt`() {
        val r = SegmentCalculator.calculate("100€")
        assertEquals(SmsEncoding.GSM7, r.encoding)
        assertEquals(4, r.charCount)
        assertEquals(5, r.billedUnits) // 3 Ziffern + 2 fuer das Euro-Zeichen
        assertEquals(1, r.segments)
    }

    @Test
    fun `leerer Text ergibt 0 Segmente`() {
        val r = SegmentCalculator.calculate("")
        assertEquals(0, r.charCount)
        assertEquals(0, r.billedUnits)
        assertEquals(0, r.segments)
        assertEquals(0, r.totalSegments(40))
    }

    // --- Grenzfaelle UCS-2 -------------------------------------------------

    @Test
    fun `70 UCS-2-Einheiten ergeben 1 Segment`() {
        val r = SegmentCalculator.calculate("…" + ascii(69))
        assertEquals(SmsEncoding.UCS2, r.encoding)
        assertEquals(70, r.billedUnits)
        assertEquals(1, r.segments)
    }

    @Test
    fun `71 UCS-2-Einheiten ergeben 2 Segmente`() {
        val r = SegmentCalculator.calculate("…" + ascii(70))
        assertEquals(2, r.segments)
    }

    @Test
    fun `134 UCS-2-Einheiten ergeben 2 Segmente`() {
        val r = SegmentCalculator.calculate("…" + ascii(133)) // 2 x 67
        assertEquals(134, r.billedUnits)
        assertEquals(2, r.segments)
    }

    @Test
    fun `135 UCS-2-Einheiten ergeben 3 Segmente`() {
        assertEquals(3, SegmentCalculator.calculate("…" + ascii(134)).segments)
    }

    // --- Greedy-Packung: 2er-Zeichen duerfen nicht zerrissen werden --------

    @Test
    fun `Euro-Zeichen an der Segmentgrenze erzwingt ein zusaetzliches Segment`() {
        // 152 ASCII fuellen Segment 1 bis auf 1 freies Septett. Das Euro-Zeichen
        // braucht 2 und muss deshalb komplett nach Segment 2 wandern - das eine
        // freie Septett in Segment 1 verfaellt.
        // Naiv gerechnet: 152 + 2 + 152 = 306 -> ceil(306/153) = 2.
        // Tatsaechlich 3, weil Segment 1 nur 152 der 153 Septetts nutzen kann.
        val text = ascii(152) + "€" + ascii(152)
        val r = SegmentCalculator.calculate(text)
        assertEquals(SmsEncoding.GSM7, r.encoding)
        assertEquals(306, r.billedUnits)
        assertEquals(3, r.segments)
    }

    @Test
    fun `Emoji an der Segmentgrenze erzwingt ein zusaetzliches Segment`() {
        // 66 Einheiten belegt, Emoji braucht 2 -> rutscht ganz in Segment 2.
        val text = "…" + ascii(65) + "🚒" + ascii(66)
        val r = SegmentCalculator.calculate(text)
        assertEquals(SmsEncoding.UCS2, r.encoding)
        assertEquals(134, r.billedUnits)
        assertEquals(3, r.segments)
    }

    // --- Erkennung der Ausloeser ------------------------------------------

    @Test
    fun `typografischer Apostroph wird als Ausloeser benannt`() {
        val r = SegmentCalculator.calculate("Der Einsatz ist beendet, alles o’k")
        assertEquals(SmsEncoding.UCS2, r.encoding)
        assertEquals(1, r.offenders.size)
        assertEquals("’", r.offenders[0].char)
        assertTrue(r.offenders[0].label.contains("Apostroph"))
    }

    @Test
    fun `mehrfach vorkommende Ausloeser werden gezaehlt`() {
        val r = SegmentCalculator.calculate("a…b…c–d")
        assertEquals(2, r.offenders.size)
        assertEquals(2, r.offenders.first { it.char == "…" }.count)
        assertEquals(1, r.offenders.first { it.char == "–" }.count)
    }

    @Test
    fun `Emoji wird als Emoji benannt`() {
        val r = SegmentCalculator.calculate("Einsatz 🚒")
        assertEquals(1, r.offenders.size)
        assertTrue(r.offenders[0].label.startsWith("Emoji"))
    }

    @Test
    fun `ein einziges Fremdzeichen kippt die gesamte Nachricht`() {
        // Ohne die Auslassungspunkte waeren 200 Zeichen 2 Segmente (GSM-7, 153/Teil).
        // Das eine Fremdzeichen macht 3 daraus - 50 Prozent Mehrkosten fuer ein Zeichen.
        assertEquals(2, SegmentCalculator.calculate(ascii(200)).segments)

        val r = SegmentCalculator.calculate(ascii(200) + "…")
        assertEquals(SmsEncoding.UCS2, r.encoding)
        assertEquals(201, r.billedUnits)
        assertEquals(3, r.segments) // ceil(201/67)
    }

    // --- Hochrechnung auf die Gruppe --------------------------------------

    @Test
    fun `Gesamtzahl ist Segmente mal Empfaenger`() {
        val r = SegmentCalculator.calculate(ascii(248))
        assertEquals(2, r.segments)
        assertEquals(30, r.totalSegments(15))
    }

    @Test
    fun `Beispiel aus der Anforderung 248 Zeichen UCS-2 ergibt 4 Segmente`() {
        val r = SegmentCalculator.calculate("…" + ascii(247))
        assertEquals(SmsEncoding.UCS2, r.encoding)
        assertEquals(248, r.billedUnits)
        assertEquals(4, r.segments)
        assertEquals(60, r.totalSegments(15))
    }
}
