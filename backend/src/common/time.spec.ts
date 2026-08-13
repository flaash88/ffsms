import {
  isoWeekNumber,
  isoWeekRange,
  monthRange,
  previousMonthRange,
} from './time';

const TZ = 'Europe/Vienna';

describe('Zeitraeume in Europe/Vienna', () => {
  it('setzt den Monatsbeginn auf Mitternacht Ortszeit, nicht UTC', () => {
    // 13.08.2026 ist Sommerzeit (UTC+2), Monatsbeginn ist also 31.07. 22:00 UTC.
    const { from, to } = monthRange(new Date('2026-08-13T18:22:00+02:00'), TZ);
    expect(from.toISOString()).toBe('2026-07-31T22:00:00.000Z');
    expect(to.toISOString()).toBe('2026-08-31T22:00:00.000Z');
  });

  it('beruecksichtigt den Wechsel auf Winterzeit', () => {
    // November ist UTC+1, Monatsbeginn also 31.10. 23:00 UTC.
    const { from } = monthRange(new Date('2026-11-15T12:00:00Z'), TZ);
    expect(from.toISOString()).toBe('2026-10-31T23:00:00.000Z');
  });

  it('schliesst eine Aussendung um 23:30 Ortszeit am Monatsletzten noch im alten Monat ein', () => {
    // Genau der Fall, der bei UTC-Rechnung in den Folgemonat rutschen wuerde.
    const sentAt = new Date('2026-08-31T23:30:00+02:00');
    const { from, to } = monthRange(sentAt, TZ);
    expect(sentAt.getTime()).toBeGreaterThanOrEqual(from.getTime());
    expect(sentAt.getTime()).toBeLessThan(to.getTime());
    expect(from.toISOString()).toBe('2026-07-31T22:00:00.000Z');
  });

  it('rechnet ueber den Jahreswechsel', () => {
    const { from, to } = monthRange(new Date('2026-12-20T12:00:00Z'), TZ);
    expect(from.toISOString()).toBe('2026-11-30T23:00:00.000Z');
    expect(to.toISOString()).toBe('2026-12-31T23:00:00.000Z');
  });

  it('liefert den Vormonat', () => {
    const { from, to } = previousMonthRange(new Date('2026-01-15T12:00:00Z'), TZ);
    expect(from.toISOString()).toBe('2025-11-30T23:00:00.000Z');
    expect(to.toISOString()).toBe('2025-12-31T23:00:00.000Z');
  });

  it('beginnt die Woche am Montag', () => {
    // 13.08.2026 ist ein Donnerstag, Wochenbeginn ist Montag der 10.08.
    const { from, to } = isoWeekRange(new Date('2026-08-13T18:22:00+02:00'), TZ);
    expect(from.toISOString()).toBe('2026-08-09T22:00:00.000Z');
    expect(to.toISOString()).toBe('2026-08-16T22:00:00.000Z');
  });

  it('ordnet den Sonntag noch der ablaufenden Woche zu', () => {
    // Sonntag 16.08.2026 um 20:00 - der Zeitpunkt des Wochenreports.
    const reportTime = new Date('2026-08-16T20:00:00+02:00');
    const { from, to } = isoWeekRange(reportTime, TZ);
    expect(from.toISOString()).toBe('2026-08-09T22:00:00.000Z');
    expect(reportTime.getTime()).toBeLessThan(to.getTime());
  });

  it('zaehlt die Kalenderwoche nach ISO-8601', () => {
    expect(isoWeekNumber(new Date('2026-08-13T12:00:00+02:00'), TZ)).toBe(33);
    // 1. Januar 2026 ist ein Donnerstag und gehoert damit zu KW 1.
    expect(isoWeekNumber(new Date('2026-01-01T12:00:00+01:00'), TZ)).toBe(1);
    // 1. Januar 2027 ist ein Freitag und gehoert noch zur KW 53 von 2026.
    expect(isoWeekNumber(new Date('2027-01-01T12:00:00+01:00'), TZ)).toBe(53);
  });
});
