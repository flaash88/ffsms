package cc.netwokx.ffsms.domain.sms

/** Kodierung einer SMS. Bestimmt Kapazitaet je Segment. */
enum class SmsEncoding {
    GSM7,
    UCS2,
    ;

    /** Kapazitaet einer Einzel-SMS (ohne UDH). */
    val singleCapacity: Int get() = if (this == GSM7) 160 else 70

    /** Kapazitaet je Teil einer Multipart-SMS (UDH belegt Platz). */
    val multipartCapacity: Int get() = if (this == GSM7) 153 else 67
}

/**
 * Ein Zeichen, das GSM-7 verhindert - inkl. Klartextbezeichnung fuer die UI.
 * @param char das Zeichen selbst (bei Surrogate-Paaren der volle Codepoint als String)
 * @param label z. B. "typografischer Apostroph ’"
 * @param count wie oft es im Text vorkommt
 */
data class OffendingChar(
    val char: String,
    val label: String,
    val count: Int,
)

/**
 * Ergebnis der Segmentberechnung.
 *
 * @param encoding ermittelte Kodierung fuer den GESAMTEN Text
 * @param charCount Anzahl der Zeichen (Codepoints, nicht UTF-16-Einheiten)
 * @param billedUnits abgerechnete Einheiten: Septetts bei GSM-7, UTF-16-Einheiten bei UCS-2
 * @param segments Anzahl SMS-Segmente fuer EINEN Empfaenger
 * @param capacityPerSegment Kapazitaet des aktuell verwendeten Segmenttyps
 * @param remainingInLastSegment noch freie Einheiten im letzten Segment
 * @param offenders Zeichen, die UCS-2 erzwungen haben (leer bei GSM-7)
 */
data class SegmentInfo(
    val encoding: SmsEncoding,
    val charCount: Int,
    val billedUnits: Int,
    val segments: Int,
    val capacityPerSegment: Int,
    val remainingInLastSegment: Int,
    val offenders: List<OffendingChar>,
) {
    val isMultipart: Boolean get() = segments > 1

    /** Gesamtzahl abgerechneter SMS fuer eine Empfaengerzahl. */
    fun totalSegments(recipients: Int): Int = segments * recipients
}

/**
 * Berechnet Kodierung und Segmentanzahl einer SMS.
 *
 * Bewusst frei von Android-Abhaengigkeiten, damit die Regeln in reinen
 * JVM-Unit-Tests festgenagelt werden koennen. Diese Klasse ist die
 * Vertrauensbasis fuer die Kostenanzeige - sie wird vor dem Versand
 * angezeigt UND im Bestaetigungsdialog wiederholt.
 */
object SegmentCalculator {

    fun calculate(text: String): SegmentInfo {
        if (text.isEmpty()) {
            return SegmentInfo(
                encoding = SmsEncoding.GSM7,
                charCount = 0,
                billedUnits = 0,
                segments = 0,
                capacityPerSegment = SmsEncoding.GSM7.singleCapacity,
                remainingInLastSegment = SmsEncoding.GSM7.singleCapacity,
                offenders = emptyList(),
            )
        }

        val offenders = findOffenders(text)
        val encoding = if (offenders.isEmpty()) SmsEncoding.GSM7 else SmsEncoding.UCS2

        // Kosten je "unteilbarer Einheit". Bei GSM-7 ist das ein Zeichen
        // (1 oder 2 Septetts), bei UCS-2 ein Codepoint (1 oder 2 UTF-16-Einheiten).
        val costs: List<Int> = when (encoding) {
            SmsEncoding.GSM7 -> text.map { GsmCharset.septetCost(it) ?: 1 }
            SmsEncoding.UCS2 -> text.codePoints().toArray().map { Character.charCount(it) }
        }

        val billedUnits = costs.sum()
        val segments = packSegments(costs, encoding)
        val capacity =
            if (segments > 1) encoding.multipartCapacity else encoding.singleCapacity
        val used = if (segments > 1) usedInLastSegment(costs, encoding) else billedUnits

        return SegmentInfo(
            encoding = encoding,
            charCount = text.codePointCount(0, text.length),
            billedUnits = billedUnits,
            segments = segments,
            capacityPerSegment = capacity,
            remainingInLastSegment = capacity - used,
            offenders = offenders,
        )
    }

    /**
     * Verteilt die Einheiten auf Segmente.
     *
     * Greedy-Packung statt simpler Division: ein 2-Einheiten-Zeichen (€ in GSM-7,
     * Emoji in UCS-2) darf NICHT ueber eine Segmentgrenze zerrissen werden. Bei
     * einer reinen Division wuerde man deshalb in Grenzfaellen ein Segment zu
     * wenig ausweisen - und genau das waere eine unterschaetzte Rechnung.
     */
    private fun packSegments(costs: List<Int>, encoding: SmsEncoding): Int {
        val total = costs.sum()
        if (total == 0) return 0
        if (total <= encoding.singleCapacity) return 1

        val cap = encoding.multipartCapacity
        var segments = 1
        var used = 0
        for (cost in costs) {
            if (used + cost > cap) {
                segments++
                used = cost
            } else {
                used += cost
            }
        }
        return segments
    }

    private fun usedInLastSegment(costs: List<Int>, encoding: SmsEncoding): Int {
        val cap = encoding.multipartCapacity
        var used = 0
        for (cost in costs) {
            used = if (used + cost > cap) cost else used + cost
        }
        return used
    }

    /** Alle Zeichen, die nicht GSM-7-kodierbar sind - gruppiert und benannt. */
    fun findOffenders(text: String): List<OffendingChar> {
        val counts = LinkedHashMap<String, Int>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val width = Character.charCount(cp)
            val s = String(Character.toChars(cp))
            if (width > 1 || !GsmCharset.isEncodable(text[i])) {
                counts[s] = (counts[s] ?: 0) + 1
            }
            i += width
        }
        return counts.map { (s, n) -> OffendingChar(s, describe(s), n) }
    }

    /**
     * Klartextbezeichnung fuer die Warnung im Verfassen-Screen.
     * Die haeufigen Stolperfallen werden namentlich genannt, alles andere
     * bekommt eine generische, aber immer noch verstaendliche Beschreibung.
     */
    fun describe(s: String): String = when (s) {
        "’" -> "typografischer Apostroph ’"
        "‘" -> "typografisches Anfuehrungszeichen ‘"
        "‚" -> "einfaches Anfuehrungszeichen unten ‚"
        "„" -> "Anfuehrungszeichen unten „"
        "“" -> "Anfuehrungszeichen oben “"
        "”" -> "Anfuehrungszeichen oben ”"
        "«" -> "Guillemet «"
        "»" -> "Guillemet »"
        "–" -> "Gedankenstrich (Halbgeviert) –"
        "—" -> "Gedankenstrich (Geviert) —"
        "…" -> "Auslassungspunkte …"
        Char(0x00A0).toString() -> "geschuetztes Leerzeichen"
        Char(0x202F).toString() -> "schmales geschuetztes Leerzeichen"
        "•" -> "Aufzaehlungspunkt •"
        "°" -> "Gradzeichen °"
        "\t" -> "Tabulator"
        else -> {
            val cp = s.codePointAt(0)
            when {
                isEmoji(cp) -> "Emoji $s"
                Character.isLetter(cp) -> "Sonderbuchstabe $s"
                else -> "Sonderzeichen $s (U+%04X)".format(cp)
            }
        }
    }

    private fun isEmoji(cp: Int): Boolean =
        cp in 0x1F000..0x1FAFF || // Emoji-Bloecke ausserhalb der BMP
            cp in 0x2600..0x27BF || // Misc symbols / Dingbats
            cp in 0x2190..0x21FF || // Pfeile
            cp == 0xFE0F || cp == 0x20E3 // Variation Selector / Keycap
}
