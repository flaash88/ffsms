package cc.netwokx.ffsms.domain.sms

import java.text.Normalizer

/**
 * Ergebnis der Bereinigung.
 *
 * @param text der bereinigte Text
 * @param replaced Zeichen, die durch ein GSM-7-Aequivalent ersetzt wurden
 * @param removed Zeichen, die ersatzlos entfernt wurden (Emojis o. ae.)
 */
data class SanitizeResult(
    val text: String,
    val replaced: List<String>,
    val removed: List<String>,
) {
    val changed: Boolean get() = replaced.isNotEmpty() || removed.isNotEmpty()
}

/**
 * Ersetzt typografische Zeichen durch ihre GSM-7-Entsprechung und entfernt
 * alles, was danach immer noch UCS-2 erzwingen wuerde.
 *
 * Der Sinn: eine Nachricht mit einem einzigen "’" kostet mehr als das Doppelte
 * einer identischen Nachricht mit "'". Die Bereinigung macht diesen Unterschied
 * mit einem Tastendruck verfuegbar - und die Anzeige springt danach sichtbar
 * auf GSM-7 zurueck.
 *
 * Es wird bewusst NICHT still im Hintergrund bereinigt: der Einsatzleiter muss
 * sehen, was mit seinem Text passiert ist.
 */
object TextSanitizer {

    /** Direkte 1:n-Ersetzungen. Alle Ziele sind GSM-7-kodierbar. */
    private val REPLACEMENTS: Map<Char, String> = buildMap {
        // Apostrophe / einfache Anfuehrungszeichen
        put('‘', "'"); put('’', "'"); put('‚', "'"); put('‛', "'")
        put('′', "'"); put('´', "'"); put('`', "'")
        // Doppelte Anfuehrungszeichen
        put('“', "\""); put('”', "\""); put('„', "\""); put('‟', "\"")
        put('″', "\""); put('«', "\""); put('»', "\"")
        // Striche
        put('‐', "-"); put('‑', "-"); put('‒', "-")
        put('–', "-"); put('—', "-"); put('―', "-"); put('−', "-")
        // Auslassungspunkte
        put('…', "...")
        // Leerzeichen-Varianten (geschuetzt, schmal, Tab, ...)
        listOf(0x00A0, 0x202F, 0x2007, 0x2009, 0x200A, 0x2002, 0x2003, 0x2005, 0x2008)
            .forEach { put(Char(it), " ") }
        put('\t', " ")
        // Aufzaehlung und haeufige Symbole
        put('•', "-"); put('·', "-"); put('⁃', "-")
        put(Char(0x00B0), " Grad")
        put('™', "(TM)"); put('©', "(C)"); put('®', "(R)")
        put('≤', "<="); put('≥', ">="); put('≠', "!=")
        put('×', "x"); put('→', "->"); put('←', "<-")
        // Zeilenenden vereinheitlichen: CRLF -> LF passiert separat
        put(Char(0x2028), "\n"); put(Char(0x2029), "\n")
    }

    fun sanitize(input: String): SanitizeResult {
        val replaced = LinkedHashSet<String>()
        val removed = LinkedHashSet<String>()
        val out = StringBuilder(input.length)

        // Windows-Zeilenenden zuerst normalisieren, damit \r nicht als
        // eigenstaendiges Zeichen mitgezaehlt wird.
        val normalizedNewlines = input.replace("\r\n", "\n")

        var i = 0
        while (i < normalizedNewlines.length) {
            val cp = normalizedNewlines.codePointAt(i)
            val width = Character.charCount(cp)
            val original = String(Character.toChars(cp))

            when {
                // Bereits GSM-7-tauglich: unveraendert uebernehmen.
                width == 1 && GsmCharset.isEncodable(normalizedNewlines[i]) -> {
                    out.append(original)
                }
                // Bekannte typografische Entsprechung.
                width == 1 && REPLACEMENTS.containsKey(normalizedNewlines[i]) -> {
                    out.append(REPLACEMENTS.getValue(normalizedNewlines[i]))
                    replaced.add(original)
                }
                else -> {
                    // Letzter Versuch: Akzente abstreifen (é -> e). Nur fuer
                    // Zeichen, die NICHT ohnehin schon GSM-7 sind - ä ö ü bleiben
                    // dadurch garantiert unangetastet.
                    val stripped = stripDiacritics(original)
                    if (stripped != null) {
                        out.append(stripped)
                        replaced.add(original)
                    } else {
                        // Emoji und alles andere Nichtdarstellbare: ersatzlos weg.
                        removed.add(original)
                    }
                }
            }
            i += width
        }

        // Durch entfernte Emojis koennen doppelte Leerzeichen entstehen.
        val cleaned = out.toString()
            .replace(Regex("[ ]{2,}"), " ")
            .lines().joinToString("\n") { it.trimEnd() }
            .trim()

        return SanitizeResult(cleaned, replaced.toList(), removed.toList())
    }

    /** Gibt die diakritikfreie GSM-7-Variante zurueck, oder null wenn es keine gibt. */
    private fun stripDiacritics(s: String): String? {
        val decomposed = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        if (decomposed.isEmpty()) return null
        return if (GsmCharset.isEncodable(decomposed)) decomposed else null
    }
}
