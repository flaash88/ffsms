import type { NtfyMessage } from './ntfy.service';

export interface AlertInput {
  deviceId: string;
  totalSegments: number;
  recipients: number;
  segmentsPerMsg: number;
  encoding: string;
  failed: number;
  abortedReason: string | null;
  /** Segmentsumme des laufenden Monats INKLUSIVE dieser Aussendung. */
  monthTotal: number;
}

export interface AlertThresholds {
  segmentsPerCampaign: number;
  segmentsPerMonth: number;
}

/**
 * Entscheidet, ob eine eingehende Aussendung einen Sofort-Alarm ausloest.
 *
 * Bewusst eine reine Funktion ohne Datenbank- oder Netzzugriff: die
 * Alarmbedingungen sind der Grund, warum es dieses Backend gibt, und sie
 * muessen sich ohne laufende Umgebung testen lassen.
 *
 * Der Titel benennt den Grund direkt - eine Push-Meldung wird auf dem
 * Sperrbildschirm gelesen, nicht aufgeklappt.
 */
export function evaluateAlert(
  input: AlertInput,
  thresholds: AlertThresholds,
): NtfyMessage | null {
  const reasons: string[] = [];
  let title: string | null = null;

  // Reihenfolge = Rangfolge. Ein Abbruch ist die wichtigste Meldung: er
  // bedeutet, dass eine Sicherung gegriffen hat.
  if (input.abortedReason) {
    title = `Aussendung ABGEBROCHEN (${input.abortedReason})`;
    reasons.push(`Abbruchgrund: ${input.abortedReason}`);
  }

  if (input.totalSegments > thresholds.segmentsPerCampaign) {
    title ??= `Grosse Aussendung: ${input.totalSegments} Segmente`;
    reasons.push(
      `${input.totalSegments} Segmente in einer Aussendung ` +
        `(Schwelle ${thresholds.segmentsPerCampaign})`,
    );
  }

  if (input.monthTotal > thresholds.segmentsPerMonth) {
    title ??= `Monatssumme ueberschritten: ${input.monthTotal} Segmente`;
    reasons.push(
      `Monat bisher ${input.monthTotal} Segmente (Schwelle ${thresholds.segmentsPerMonth})`,
    );
  }

  if (input.failed > 0) {
    title ??= `${input.failed} Empfaenger fehlgeschlagen`;
    reasons.push(`${input.failed} Empfaenger konnten nicht bedient werden`);
  }

  if (title === null) {
    return null;
  }

  const body = [
    `Geraet: ${input.deviceId}`,
    `${input.recipients} Empfaenger x ${input.segmentsPerMsg} Segmente = ${input.totalSegments}`,
    `Encoding: ${input.encoding}${input.encoding === 'UCS2' ? ' (Sonderzeichen!)' : ''}`,
    `Monat bisher: ${input.monthTotal} Segmente`,
    '',
    ...reasons.map((reason) => `- ${reason}`),
  ].join('\n');

  return {
    title,
    body,
    priority: 'high',
    tags: input.abortedReason ? ['rotating_light'] : ['warning'],
  };
}
