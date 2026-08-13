import { evaluateAlert, type AlertInput, type AlertThresholds } from './alerts';

const THRESHOLDS: AlertThresholds = {
  segmentsPerCampaign: 150,
  segmentsPerMonth: 1500,
};

function campaign(overrides: Partial<AlertInput> = {}): AlertInput {
  return {
    deviceId: 'ff-kuehwiesen-sms',
    totalSegments: 45,
    recipients: 15,
    segmentsPerMsg: 3,
    encoding: 'GSM7',
    failed: 0,
    abortedReason: null,
    monthTotal: 200,
    ...overrides,
  };
}

describe('Sofort-Alarm', () => {
  it('schweigt bei einer unauffaelligen Aussendung', () => {
    expect(evaluateAlert(campaign(), THRESHOLDS)).toBeNull();
  });

  it('meldet eine Aussendung ueber der Segmentschwelle', () => {
    const alert = evaluateAlert(campaign({ totalSegments: 151 }), THRESHOLDS);
    expect(alert).not.toBeNull();
    expect(alert!.title).toContain('151');
    expect(alert!.priority).toBe('high');
  });

  it('meldet nicht bei genau der Schwelle', () => {
    expect(evaluateAlert(campaign({ totalSegments: 150 }), THRESHOLDS)).toBeNull();
  });

  it('meldet eine ueberschrittene Monatssumme', () => {
    const alert = evaluateAlert(campaign({ monthTotal: 1501 }), THRESHOLDS);
    expect(alert!.title).toContain('Monatssumme');
    expect(alert!.body).toContain('1501');
  });

  it('meldet fehlgeschlagene Empfaenger', () => {
    const alert = evaluateAlert(campaign({ failed: 2 }), THRESHOLDS);
    expect(alert!.title).toContain('2 Empfaenger');
  });

  it('stellt einen Abbruch ueber alle anderen Gruende', () => {
    const alert = evaluateAlert(
      campaign({ abortedReason: 'limit_daily', totalSegments: 900, failed: 5 }),
      THRESHOLDS,
    );
    expect(alert!.title).toContain('ABGEBROCHEN');
    expect(alert!.title).toContain('limit_daily');
    expect(alert!.tags).toContain('rotating_light');
  });

  it('nennt alle zutreffenden Gruende im Text', () => {
    const alert = evaluateAlert(
      campaign({ totalSegments: 400, monthTotal: 2000, failed: 3 }),
      THRESHOLDS,
    );
    expect(alert!.body).toContain('400 Segmente in einer Aussendung');
    expect(alert!.body).toContain('Monat bisher 2000 Segmente');
    expect(alert!.body).toContain('3 Empfaenger konnten nicht bedient werden');
  });

  it('markiert UCS-2 im Meldungstext', () => {
    const alert = evaluateAlert(
      campaign({ encoding: 'UCS2', totalSegments: 200 }),
      THRESHOLDS,
    );
    expect(alert!.body).toContain('UCS2 (Sonderzeichen!)');
  });

  it('haette den 2100er-Vorfall gemeldet', () => {
    const alert = evaluateAlert(
      campaign({ totalSegments: 2100, recipients: 30, segmentsPerMsg: 70, monthTotal: 2100 }),
      THRESHOLDS,
    );
    expect(alert).not.toBeNull();
    expect(alert!.priority).toBe('high');
    expect(alert!.title).toContain('2100');
  });
});
