import { formatWeeklyReport, type WeeklyReportInput } from './weekly-report';

function input(overrides: Partial<WeeklyReportInput> = {}): WeeklyReportInput {
  return {
    label: 'FF Kuehwiesen',
    weekNumber: 33,
    campaigns: 4,
    recipients: 61,
    totalSegments: 178,
    byEncoding: { GSM7: 3, UCS2: 1 },
    monthSoFar: 512,
    previousMonthTotal: 634,
    abortedCount: 0,
    failedCount: 0,
    ...overrides,
  };
}

describe('Wochenreport', () => {
  it('entspricht dem vereinbarten Aufbau', () => {
    const report = formatWeeklyReport(input());

    expect(report.title).toBe('FF Kuehwiesen - SMS-Woche 33');
    expect(report.body).toBe(
      [
        'Aussendungen: 4',
        'Empfaenger gesamt: 61',
        'Segmente (verrechnet): 178',
        'Encoding: 3x GSM-7, 1x UCS-2 (!)',
        'Monat bisher: 512 Segmente',
        'Vormonat gesamt: 634',
      ].join('\n'),
    );
    expect(report.priority).toBe('default');
  });

  it('markiert UCS-2 nicht, wenn es keines gab', () => {
    const report = formatWeeklyReport(input({ byEncoding: { GSM7: 4 } }));
    expect(report.body).toContain('Encoding: 4x GSM-7');
    expect(report.body).not.toContain('(!)');
  });

  it('sortiert GSM-7 vor UCS-2, unabhaengig von der Eingabereihenfolge', () => {
    const report = formatWeeklyReport(input({ byEncoding: { UCS2: 1, GSM7: 3 } }));
    expect(report.body).toContain('Encoding: 3x GSM-7, 1x UCS-2 (!)');
  });

  it('kommt mit einer Woche ohne Aussendungen zurecht', () => {
    const report = formatWeeklyReport(
      input({ campaigns: 0, recipients: 0, totalSegments: 0, byEncoding: {} }),
    );
    expect(report.body).toContain('Aussendungen: 0');
    expect(report.body).toContain('Encoding: keine');
  });

  it('ergaenzt Abbrueche und Fehler nur wenn es welche gab', () => {
    const clean = formatWeeklyReport(input());
    expect(clean.body).not.toContain('Abbrueche');
    expect(clean.body).not.toContain('Fehlgeschlagene');

    const problems = formatWeeklyReport(input({ abortedCount: 1, failedCount: 2 }));
    expect(problems.body).toContain('Abbrueche: 1');
    expect(problems.body).toContain('Fehlgeschlagene Empfaenger: 2');
    expect(problems.priority).toBe('high');
  });
});
