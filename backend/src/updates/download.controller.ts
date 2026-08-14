import {
  Controller,
  Get,
  Header,
  Res,
  StreamableFile,
  UseGuards,
} from '@nestjs/common';
import type { Response } from 'express';
import { BasicAuthGuard } from '../common/basic-auth.guard';
import { UpdatesService } from './updates.service';

/**
 * Download-Seite fuer die Erstinstallation.
 *
 * Der Update-Kanal unter /api/v1/update liegt hinter dem API-Key und ist fuer
 * die bereits installierte App gedacht. Beim ersten Mal gibt es die App aber
 * noch nicht - und damit auch niemanden, der einen Header setzen koennte. Fuer
 * diesen einen Fall gibt es hier eine Seite, die sich mit einem Passwort im
 * Browser oeffnen laesst: Link durchgeben, Passwort durchsagen, fertig.
 *
 * Dieselbe Datei, derselbe Server. Ein zweiter Webserver nur zum Ausliefern
 * einer Datei waere ein zweites Ding, das gepflegt, aktualisiert und beim
 * naechsten Umzug mitgenommen werden muss - und eine zweite Stelle, an der
 * eine Zugriffsregel falsch stehen kann.
 */
@Controller('download')
@UseGuards(BasicAuthGuard)
export class DownloadController {
  constructor(private readonly updates: UpdatesService) {}

  @Get()
  @Header('Content-Type', 'text/html; charset=utf-8')
  @Header('Cache-Control', 'no-store')
  async page(): Promise<string> {
    const manifest = await this.updates.manifest().catch(() => null);
    return renderPage(manifest);
  }

  /**
   * Der Dateiname traegt die Version.
   *
   * Im Download-Ordner eines Handys liegen sonst app-release.apk,
   * app-release(1).apk und app-release(2).apk nebeneinander, und niemand
   * weiss mehr, welche davon die neue ist.
   */
  @Get('app.apk')
  async apk(@Res({ passthrough: true }) response: Response): Promise<StreamableFile> {
    const manifest = await this.updates.manifest();
    response.setHeader('Content-Type', 'application/vnd.android.package-archive');
    response.setHeader(
      'Content-Disposition',
      `attachment; filename="ff-sms-${manifest.version_name}.apk"`,
    );
    response.setHeader('Content-Length', String(manifest.size_bytes));
    return new StreamableFile(await this.updates.apkStream());
  }
}

interface PageData {
  version_name: string;
  version_code: number;
  size_bytes: number;
  sha256: string;
  released_at: string;
  notes: string | null;
}

function renderPage(manifest: PageData | null): string {
  const body = manifest
    ? `
      <p class="version">Version ${escapeHtml(manifest.version_name)}
        <span class="build">(Build ${manifest.version_code})</span></p>
      <p class="meta">${formatSize(manifest.size_bytes)} &middot;
        bereitgestellt am ${formatDate(manifest.released_at)}</p>
      ${manifest.notes ? `<p class="notes">${escapeHtml(manifest.notes)}</p>` : ''}
      <a class="button" href="/download/app.apk">APK herunterladen</a>
      <h2>Installation</h2>
      <ol>
        <li>Datei herunterladen und im Browser oder in „Downloads" antippen.</li>
        <li>Android fragt, ob aus dieser Quelle installiert werden darf —
            erlauben.</li>
        <li>Meldet Play&nbsp;Protect „App nicht sicher" oder bricht die
            Installation mit <code>VERIFICATION_FAILURE</code> ab: unter
            „Mehr Details" auf <em>Trotzdem installieren</em> tippen. Die App
            ist selbst signiert und daher nicht bei Google registriert.</li>
        <li>Nach dem ersten Start unter <em>Einstellungen</em> die
            Serveradresse und den Schlüssel eintragen.</li>
      </ol>
      <h2>Prüfsumme</h2>
      <p class="meta">Zum Vergleichen, falls der Download abgebrochen ist.
        Die App prüft sie bei ihren eigenen Updates automatisch.</p>
      <code class="hash">${escapeHtml(manifest.sha256)}</code>`
    : `
      <p class="empty">Zurzeit liegt keine Version bereit.</p>
      <p class="meta">Es fehlt <code>app-release.apk</code> oder
        <code>release.json</code> im Update-Verzeichnis des Servers.</p>`;

  return `<!doctype html>
<html lang="de">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>FF-SMS herunterladen</title>
<style>
  :root { color-scheme: light dark; --fg: #1a1a1a; --muted: #5c5c5c;
          --bg: #fbfbfa; --card: #fff; --line: #e3e3e0; --accent: #a33a1f; }
  @media (prefers-color-scheme: dark) {
    :root { --fg: #eceae5; --muted: #a3a099; --bg: #191917; --card: #232320;
            --line: #35342f; --accent: #e2795a; }
  }
  * { box-sizing: border-box; }
  body { margin: 0; padding: 2rem 1rem; background: var(--bg); color: var(--fg);
         font: 16px/1.6 system-ui, -apple-system, "Segoe UI", sans-serif; }
  main { max-width: 34rem; margin: 0 auto; background: var(--card);
         border: 1px solid var(--line); border-radius: 12px; padding: 1.75rem; }
  h1 { margin: 0 0 .25rem; font-size: 1.4rem; }
  h2 { margin: 2rem 0 .5rem; font-size: 1rem; text-transform: uppercase;
       letter-spacing: .05em; color: var(--muted); }
  .version { margin: 1.25rem 0 .25rem; font-size: 1.15rem; font-weight: 600; }
  .build, .meta { color: var(--muted); font-weight: 400; font-size: .9rem; }
  .meta { margin: .25rem 0 1.25rem; }
  .notes { margin: .75rem 0 1.25rem; padding-left: .75rem;
           border-left: 3px solid var(--line); }
  .button { display: block; text-align: center; padding: .85rem 1rem;
            background: var(--accent); color: #fff; text-decoration: none;
            border-radius: 8px; font-weight: 600; }
  ol { padding-left: 1.25rem; }
  li { margin: .5rem 0; }
  code { font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: .85em; }
  .hash { display: block; word-break: break-all; color: var(--muted);
          background: var(--bg); border: 1px solid var(--line);
          border-radius: 6px; padding: .6rem; }
  .empty { font-weight: 600; }
  footer { max-width: 34rem; margin: 1rem auto 0; color: var(--muted);
           font-size: .8rem; text-align: center; }
</style>
</head>
<body>
<main>
  <h1>FF-SMS</h1>
  <p class="meta">SMS-Verteiler der Feuerwehr</p>
  ${body}
</main>
<footer>Nur für den internen Gebrauch. Bitte nicht weitergeben.</footer>
</body>
</html>
`;
}

function formatSize(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDate(iso: string): string {
  const date = new Date(iso);
  return new Intl.DateTimeFormat('de-AT', {
    dateStyle: 'long',
    timeZone: 'Europe/Vienna',
  }).format(date);
}

/**
 * version_name und notes stammen aus release.json auf dem Server. Das ist
 * keine feindliche Quelle, aber ein Anfuehrungszeichen in den Notizen soll
 * die Seite nicht zerlegen.
 */
function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
