package cc.netwokx.ffsms.domain.sms

/**
 * GSM 03.38 / 3GPP TS 23.038 Zeichensatz.
 *
 * Wichtig fuer dieses Projekt: die deutschen Umlaute und das scharfe s
 * (ä ö ü ß Ä Ö Ü) sind Teil des GSM-7-BASISALPHABETS. Sie kosten genau
 * 1 Septett und loesen KEIN UCS-2 aus. Das wird haeufig falsch angenommen
 * und fuehrt dann zu einer massiv ueberschaetzten oder unterschaetzten
 * Segmentzahl.
 *
 * Nicht enthalten sind dagegen die typografischen Varianten, die Handys,
 * Word und iOS gerne automatisch einsetzen: ’ ‘ „ “ – — … sowie das
 * geschuetzte Leerzeichen. Genau die kippen eine Nachricht auf UCS-2.
 */
object GsmCharset {

    /**
     * Basisalphabet. Ein Zeichen daraus kostet 1 Septett.
     * ESC (0x1B) ist bewusst NICHT enthalten - es ist ein Steuerzeichen
     * und nur als Praefix der Extension-Tabelle gueltig.
     */
    val BASIC: Set<Char> = buildSet {
        // 0x00 - 0x0F
        addAll("@£$¥èéùìòÇ\nØø\rÅå".toList())
        // 0x10 - 0x1F (ohne ESC an Position 0x1B)
        addAll("Δ_ΦΓΛΩΠΨΣΘΞÆæßÉ".toList())
        // 0x20 - 0x2F
        addAll(" !\"#¤%&'()*+,-./".toList())
        // 0x30 - 0x3F
        addAll("0123456789:;<=>?".toList())
        // 0x40 - 0x4F
        addAll("¡ABCDEFGHIJKLMNO".toList())
        // 0x50 - 0x5F
        addAll("PQRSTUVWXYZÄÖÑÜ§".toList())
        // 0x60 - 0x6F
        addAll("¿abcdefghijklmno".toList())
        // 0x70 - 0x7F
        addAll("pqrstuvwxyzäöñüà".toList())
    }

    /**
     * Extension-Tabelle. Diese Zeichen werden als ESC + Zeichen kodiert und
     * kosten daher 2 Septetts - erzwingen aber KEIN UCS-2.
     * Das Euro-Zeichen ist der praxisrelevanteste Fall.
     */
    val EXTENDED: Set<Char> = setOf(
        Char(0x0C), // Form Feed
        '^', '{', '}', '\\', '[', '~', ']', '|',
        '€', // Euro-Zeichen
    )

    /** Kosten eines Zeichens in Septetts, oder null wenn nicht GSM-7-kodierbar. */
    fun septetCost(c: Char): Int? = when {
        BASIC.contains(c) -> 1
        EXTENDED.contains(c) -> 2
        else -> null
    }

    fun isEncodable(c: Char): Boolean = septetCost(c) != null

    /** Ist der gesamte Text in GSM-7 darstellbar? Ein einziges Fremdzeichen genuegt zum Kippen. */
    fun isEncodable(text: CharSequence): Boolean = text.all { isEncodable(it) }
}
