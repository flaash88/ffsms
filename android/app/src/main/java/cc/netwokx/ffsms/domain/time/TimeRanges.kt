package cc.netwokx.ffsms.domain.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Halboffenes Intervall [from, to) in Millisekunden seit Epoch. */
data class MillisRange(val from: Long, val to: Long)

/**
 * Zeitraeume fuer die Verbrauchsanzeige.
 *
 * Bewusst in der lokalen Zeitzone gerechnet: der Betreiber vergleicht die
 * Zahlen mit seiner Providerrechnung, und die richtet sich nach Kalendertagen
 * vor Ort, nicht nach UTC.
 */
object TimeRanges {

    fun today(zone: ZoneId = ZoneId.systemDefault(), now: LocalDate = LocalDate.now(zone)): MillisRange =
        MillisRange(now.atStartOfDay(zone).toEpochMilli(), now.plusDays(1).atStartOfDay(zone).toEpochMilli())

    /** Kalenderwoche nach ISO-8601, also Montag bis Sonntag. */
    fun thisWeek(zone: ZoneId = ZoneId.systemDefault(), now: LocalDate = LocalDate.now(zone)): MillisRange {
        val monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return MillisRange(
            monday.atStartOfDay(zone).toEpochMilli(),
            monday.plusWeeks(1).atStartOfDay(zone).toEpochMilli(),
        )
    }

    fun thisMonth(zone: ZoneId = ZoneId.systemDefault(), now: LocalDate = LocalDate.now(zone)): MillisRange {
        val first = now.withDayOfMonth(1)
        return MillisRange(
            first.atStartOfDay(zone).toEpochMilli(),
            first.plusMonths(1).atStartOfDay(zone).toEpochMilli(),
        )
    }

    private fun java.time.ZonedDateTime.toEpochMilli(): Long = toInstant().toEpochMilli()

    fun instantOf(millis: Long): Instant = Instant.ofEpochMilli(millis)
}
