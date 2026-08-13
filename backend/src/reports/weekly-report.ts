import type { NtfyMessage } from '../notify/ntfy.service';

export interface WeeklyReportInput {
  /** Klartextname der Feuerwehr, aus REPORT_TITLE. */
  label: string;
  weekNumber: number;
  campaigns: number;
  recipients: number;
  totalSegments: number;
  /** Anzahl Aussendungen je Encoding, z. B. { GSM7: 3, UCS2: 1 }. */
  byEncoding: Record<string, number>;
  monthSoFar: number;
  previousMonthTotal: number;
  abortedCount: number;
  failedCount: number;
}

/**
 * Baut den Wochenreport.
 *
 * Reine Funktion, damit sich das Format ohne Datenbank und ohne ntfy testen
 * laesst - der Report ist das, was der Kommandant tatsaechlich zu sehen
 * bekommt, und soll deshalb genau festgelegt sein.
 */
export function formatWeeklyReport(input: WeeklyReportInput): NtfyMessage {
  const lines = [
    `Aussendungen: ${input.campaigns}`,
    `Empfaenger gesamt: ${input.recipients}`,
    `Segmente (verrechnet): ${input.totalSegments}`,
    `Encoding: ${formatEncodings(input.byEncoding)}`,
    `Monat bisher: ${input.monthSoFar} Segmente`,
    `Vormonat gesamt: ${input.previousMonthTotal}`,
  ];

  // Abbrueche und Fehler nur erwaehnen, wenn es welche gab. Eine Zeile
  // "Abbrueche: 0" jede Woche wuerde nur abstumpfen.
  if (input.abortedCount > 0) {
    lines.push(`Abbrueche: ${input.abortedCount}`);
  }
  if (input.failedCount > 0) {
    lines.push(`Fehlgeschlagene Empfaenger: ${input.failedCount}`);
  }

  const hasProblems = input.abortedCount > 0 || input.failedCount > 0;

  return {
    title: `${input.label} - SMS-Woche ${input.weekNumber}`,
    body: lines.join('\n'),
    priority: hasProblems ? 'high' : 'default',
    tags: hasProblems ? ['warning'] : ['bar_chart'],
  };
}

/**
 * "3x GSM-7, 1x UCS-2 (!)" - UCS-2 wird markiert, weil es die Kosten
 * mehr als verdoppelt und fast immer vermeidbar ist.
 */
function formatEncodings(byEncoding: Record<string, number>): string {
  const entries = Object.entries(byEncoding).filter(([, count]) => count > 0);
  if (entries.length === 0) {
    return 'keine';
  }

  // GSM7 zuerst, damit die Reihenfolge stabil ist.
  const order = ['GSM7', 'UCS2'];
  entries.sort(([a], [b]) => order.indexOf(a) - order.indexOf(b));

  return entries
    .map(([encoding, count]) => {
      const label = encoding === 'GSM7' ? 'GSM-7' : encoding === 'UCS2' ? 'UCS-2' : encoding;
      const marker = encoding === 'UCS2' ? ' (!)' : '';
      return `${count}x ${label}${marker}`;
    })
    .join(', ');
}
