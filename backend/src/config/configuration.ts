/**
 * Konfiguration aus Umgebungsvariablen.
 *
 * Absichtlich ohne Fallback-Geheimnisse: fehlt API_KEYS, startet die
 * Anwendung nicht. Ein Backend, das mangels Konfiguration versehentlich
 * ohne Authentifizierung laeuft, waere schlimmer als eines, das gar nicht
 * startet.
 */

export interface DeviceKey {
  deviceId: string;
  key: string;
}

export interface AppConfig {
  port: number;
  database: {
    host: string;
    port: number;
    username: string;
    password: string;
    database: string;
  };
  deviceKeys: DeviceKey[];
  ntfy: {
    url: string | null;
    token: string | null;
    /** Sofort-Alarm, wenn total_segments einer Aussendung darueber liegt. */
    alertSegmentsPerCampaign: number;
    /** Sofort-Alarm, wenn die Monatssumme darueber liegt. */
    alertSegmentsPerMonth: number;
  };
  timezone: string;
  weeklyReportCron: string;
  /** Klartextname in der Betreffzeile des Wochenreports. */
  reportTitle: string;
}

/**
 * Parst API_KEYS im Format "geraet1:schluessel1,geraet2:schluessel2".
 * Ein Schluessel gehoert immer zu genau einem Geraet - damit kann ein
 * kompromittiertes Geraet keine Daten unter fremder Kennung einliefern.
 */
export function parseDeviceKeys(raw: string | undefined): DeviceKey[] {
  if (!raw || raw.trim() === '') {
    throw new Error(
      'API_KEYS ist nicht gesetzt. Format: "geraete-kennung:schluessel[,weitere...]"',
    );
  }

  const keys = raw
    .split(',')
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0)
    .map((entry) => {
      const separator = entry.indexOf(':');
      if (separator <= 0 || separator === entry.length - 1) {
        throw new Error(
          `Ungueltiger Eintrag in API_KEYS: "${entry}". Erwartet wird "kennung:schluessel".`,
        );
      }
      const deviceId = entry.slice(0, separator).trim();
      const key = entry.slice(separator + 1).trim();
      if (key.length < 16) {
        throw new Error(
          `Der API-Key fuer "${deviceId}" ist zu kurz (mindestens 16 Zeichen).`,
        );
      }
      return { deviceId, key };
    });

  const duplicates = keys
    .map((k) => k.deviceId)
    .filter((id, index, all) => all.indexOf(id) !== index);
  if (duplicates.length > 0) {
    throw new Error(`Geraete-Kennung mehrfach in API_KEYS: ${duplicates.join(', ')}`);
  }

  return keys;
}

function intFromEnv(value: string | undefined, fallback: number): number {
  const parsed = Number.parseInt(value ?? '', 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export default (): AppConfig => ({
  port: intFromEnv(process.env.PORT, 3000),
  database: {
    host: process.env.DB_HOST ?? 'db',
    port: intFromEnv(process.env.DB_PORT, 5432),
    username: process.env.DB_USER ?? 'ffsms',
    password: process.env.DB_PASSWORD ?? '',
    database: process.env.DB_NAME ?? 'ffsms',
  },
  deviceKeys: parseDeviceKeys(process.env.API_KEYS),
  ntfy: {
    url: process.env.NTFY_URL?.trim() || null,
    token: process.env.NTFY_TOKEN?.trim() || null,
    alertSegmentsPerCampaign: intFromEnv(process.env.ALERT_SEGMENTS_PER_CAMPAIGN, 150),
    alertSegmentsPerMonth: intFromEnv(process.env.ALERT_SEGMENTS_PER_MONTH, 1500),
  },
  timezone: process.env.TZ_REPORT ?? 'Europe/Vienna',
  // Sonntag 20:00. Die Zeitzone kommt aus TZ_REPORT, nicht aus dem Cron-String.
  weeklyReportCron: process.env.WEEKLY_REPORT_CRON ?? '0 20 * * 0',
  reportTitle: process.env.REPORT_TITLE ?? 'FF-SMS',
});
