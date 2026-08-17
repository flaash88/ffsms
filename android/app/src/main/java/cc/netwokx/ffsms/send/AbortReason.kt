package cc.netwokx.ffsms.send

/**
 * Gruende, aus denen eine Aussendung abgebrochen wird.
 *
 * Der [code] geht als `aborted_reason` an das Backend und loest dort einen
 * Sofort-Alarm per ntfy aus. Der [message] steht im Verlauf der App.
 *
 * Es gibt bewusst keinen Zustand "abgebrochen, aber trotzdem weitergesendet".
 * Wenn eine dieser Bedingungen greift, endet der Versand.
 */
enum class AbortReason(val code: String, val message: String) {
    LIMIT_CAMPAIGN(
        "limit_campaign",
        "Obergrenze je Aussendung ueberschritten",
    ),
    LIMIT_DAILY(
        "limit_daily",
        "Tagesobergrenze ueberschritten",
    ),
    LIMIT_MONTHLY(
        "limit_monthly",
        "Monatsobergrenze ueberschritten",
    ),
    NO_PERMISSION(
        "no_permission",
        "Berechtigung zum SMS-Versand fehlt",
    ),
    NO_RECIPIENTS(
        "no_recipients",
        "Keine gueltigen Empfaenger",
    ),
    SMS_UNAVAILABLE(
        "sms_unavailable",
        "Kein SMS-Dienst verfuegbar (keine SIM?)",
    ),
    ;

    companion object {
        fun fromCode(code: String?): AbortReason? = entries.firstOrNull { it.code == code }
    }
}
