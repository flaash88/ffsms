package cc.netwokx.ffsms.domain.phone

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

/**
 * Ergebnis einer Normalisierung.
 *
 * @param e164 die Nummer in E.164 (+436641234567), null wenn nicht normalisierbar
 * @param raw die urspruengliche Eingabe, unveraendert
 * @param problem Klartextgrund, warum es nicht geklappt hat
 */
data class NormalizedNumber(
    val e164: String?,
    val raw: String,
    val problem: String? = null,
) {
    val isValid: Boolean get() = e164 != null
    /** Wert fuer die Datenbank: normalisiert wenn moeglich, sonst die Roheingabe. */
    val storageValue: String get() = e164 ?: raw.trim()
}

/**
 * Normalisiert Telefonnummern nach E.164.
 *
 * Ohne diesen Schritt wuerden "0664 1234567", "+43 664 1234567" und
 * "0043-664-1234567" als drei verschiedene Empfaenger gelten und dieselbe
 * Person dreimal eine SMS bekommen. Die Normalisierung ist damit Teil des
 * Schutzes gegen Mehrfachversand, nicht nur Kosmetik.
 */
class PhoneNormalizer(private val defaultRegion: String = "AT") {

    private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    fun normalize(input: String): NormalizedNumber {
        val raw = input.trim()
        if (raw.isEmpty()) {
            return NormalizedNumber(null, raw, "Leere Eingabe")
        }

        // Kurzwahlen und Sondernummern (z. B. 122) lassen sich nicht nach E.164
        // bringen und sind als SMS-Empfaenger ohnehin nicht sinnvoll.
        val digits = raw.count { it.isDigit() }
        if (digits < 5) {
            return NormalizedNumber(null, raw, "Zu wenige Ziffern")
        }

        return try {
            val parsed = util.parse(raw, defaultRegion)
            when {
                !util.isValidNumber(parsed) ->
                    NormalizedNumber(null, raw, "Keine gueltige Rufnummer")
                else -> {
                    val type = util.getNumberType(parsed)
                    val e164 = util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
                    if (type == PhoneNumberUtil.PhoneNumberType.FIXED_LINE) {
                        // Festnetz kann keine SMS empfangen - wird uebernommen,
                        // aber sichtbar markiert.
                        NormalizedNumber(null, raw, "Festnetznummer, kann keine SMS empfangen")
                    } else {
                        NormalizedNumber(e164, raw)
                    }
                }
            }
        } catch (e: NumberParseException) {
            NormalizedNumber(null, raw, "Nicht lesbar: ${e.errorType.name}")
        }
    }

    /** Kompakte Anzeigeform fuer die UI (nationale Schreibweise, sonst E.164). */
    fun formatForDisplay(e164: String): String = try {
        val parsed = util.parse(e164, defaultRegion)
        util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
    } catch (e: NumberParseException) {
        e164
    }
}
