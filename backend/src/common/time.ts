/**
 * Zeitraum-Berechnung in einer festen Zeitzone (Europe/Vienna).
 *
 * Bewusst ohne zusaetzliche Bibliothek: gebraucht werden Monats- und
 * Wochengrenzen, und die lassen sich mit Intl zuverlaessig bestimmen. Eine
 * Abhaengigkeit weniger ist bei einer Anwendung, die jahrelang unbeaufsichtigt
 * laufen soll, ein echter Vorteil.
 *
 * Wichtig ist die Zeitzone: der Betreiber vergleicht die Zahlen mit seiner
 * Providerrechnung, und die richtet sich nach oesterreichischen Kalendertagen,
 * nicht nach UTC. Ein Monatswechsel um 00:00 Ortszeit ist im Sommer 22:00 UTC
 * des Vortages - ohne Umrechnung landen Aussendungen im falschen Monat.
 */

export interface DateRange {
  from: Date;
  to: Date;
}

interface ZonedParts {
  year: number;
  month: number; // 1-12
  day: number;
  hour: number;
  minute: number;
  second: number;
}

function partsInZone(date: Date, timezone: string): ZonedParts {
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone: timezone,
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });

  const lookup: Record<string, number> = {};
  for (const part of formatter.formatToParts(date)) {
    if (part.type !== 'literal') {
      lookup[part.type] = Number.parseInt(part.value, 10);
    }
  }

  return {
    year: lookup.year,
    month: lookup.month,
    day: lookup.day,
    // Intl liefert bei hour12:false je nach Umgebung 24 statt 0 fuer Mitternacht.
    hour: lookup.hour % 24,
    minute: lookup.minute,
    second: lookup.second,
  };
}

/** Versatz der Zeitzone zu UTC in Millisekunden, zum gegebenen Zeitpunkt. */
function zoneOffset(date: Date, timezone: string): number {
  const p = partsInZone(date, timezone);
  const asUtc = Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute, p.second);
  return asUtc - date.getTime();
}

/**
 * Wandelt eine lokale Wanduhrzeit in den zugehoerigen UTC-Zeitpunkt.
 *
 * Zwei Durchlaeufe, weil der Versatz selbst vom Zeitpunkt abhaengt: an den
 * Umstellungswochenenden liefert der erste Versuch sonst eine Stunde daneben.
 */
export function zonedTimeToUtc(
  year: number,
  month: number,
  day: number,
  timezone: string,
): Date {
  const guess = Date.UTC(year, month - 1, day, 0, 0, 0);
  const firstOffset = zoneOffset(new Date(guess), timezone);
  const secondOffset = zoneOffset(new Date(guess - firstOffset), timezone);
  return new Date(guess - secondOffset);
}

/** Kalendermonat, in dem [date] liegt: [Monatsbeginn, Beginn Folgemonat). */
export function monthRange(date: Date, timezone: string): DateRange {
  const p = partsInZone(date, timezone);
  const from = zonedTimeToUtc(p.year, p.month, 1, timezone);
  const nextMonth = p.month === 12 ? 1 : p.month + 1;
  const nextYear = p.month === 12 ? p.year + 1 : p.year;
  return { from, to: zonedTimeToUtc(nextYear, nextMonth, 1, timezone) };
}

/** Der Kalendermonat davor. */
export function previousMonthRange(date: Date, timezone: string): DateRange {
  const p = partsInZone(date, timezone);
  const prevMonth = p.month === 1 ? 12 : p.month - 1;
  const prevYear = p.month === 1 ? p.year - 1 : p.year;
  return {
    from: zonedTimeToUtc(prevYear, prevMonth, 1, timezone),
    to: zonedTimeToUtc(p.year, p.month, 1, timezone),
  };
}

/** ISO-8601-Woche (Montag bis Sonntag), in der [date] liegt. */
export function isoWeekRange(date: Date, timezone: string): DateRange {
  const p = partsInZone(date, timezone);
  const localNoon = new Date(Date.UTC(p.year, p.month - 1, p.day, 12));
  // getUTCDay: 0 = Sonntag. Fuer ISO soll Montag der Wochenanfang sein.
  const isoWeekday = (localNoon.getUTCDay() + 6) % 7;
  const monday = new Date(localNoon.getTime() - isoWeekday * 86400000);

  const from = zonedTimeToUtc(
    monday.getUTCFullYear(),
    monday.getUTCMonth() + 1,
    monday.getUTCDate(),
    timezone,
  );
  const nextMonday = new Date(monday.getTime() + 7 * 86400000);
  const to = zonedTimeToUtc(
    nextMonday.getUTCFullYear(),
    nextMonday.getUTCMonth() + 1,
    nextMonday.getUTCDate(),
    timezone,
  );
  return { from, to };
}

/**
 * ISO-8601-Kalenderwochennummer.
 * Steht in der Betreffzeile des Wochenreports ("SMS-Woche 33").
 */
export function isoWeekNumber(date: Date, timezone: string): number {
  const p = partsInZone(date, timezone);
  const target = new Date(Date.UTC(p.year, p.month - 1, p.day));
  // Auf den Donnerstag derselben ISO-Woche schieben - der bestimmt das Jahr.
  const isoWeekday = (target.getUTCDay() + 6) % 7;
  target.setUTCDate(target.getUTCDate() - isoWeekday + 3);

  const firstThursday = new Date(Date.UTC(target.getUTCFullYear(), 0, 4));
  const firstIsoWeekday = (firstThursday.getUTCDay() + 6) % 7;
  firstThursday.setUTCDate(firstThursday.getUTCDate() - firstIsoWeekday + 3);

  return 1 + Math.round((target.getTime() - firstThursday.getTime()) / (7 * 86400000));
}
