import { INestApplication } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { Test } from '@nestjs/testing';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import request from 'supertest';
import { DownloadController } from './download.controller';
import { UpdatesService } from './updates.service';

const PASSWORD = 'geheim-fuers-feuerwehrhaus';
const APK_CONTENT = 'nicht wirklich ein APK, aber eine Datei mit Inhalt';

/**
 * Die Download-Seite ist der einzige Teil des Backends, der ohne API-Key
 * erreichbar ist. Entsprechend genau wird hier geprueft, wer durchkommt.
 */
describe('GET /download', () => {
  let app: INestApplication;
  let directory: string;

  async function boot(password: string | null): Promise<void> {
    const moduleRef = await Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({
          isGlobal: true,
          load: [() => ({ updateDir: directory, downloadPassword: password })],
        }),
      ],
      controllers: [DownloadController],
      providers: [UpdatesService],
    }).compile();

    app = moduleRef.createNestApplication();
    await app.init();
  }

  function auth(password: string): string {
    return 'Basic ' + Buffer.from(`egal:${password}`).toString('base64');
  }

  beforeEach(async () => {
    directory = await mkdtemp(join(tmpdir(), 'ffsms-download-'));
    await writeFile(join(directory, 'app-release.apk'), APK_CONTENT);
    await writeFile(
      join(directory, 'release.json'),
      JSON.stringify({ version_code: 3, version_name: '1.1.1', notes: 'Testlauf' }),
    );
  });

  afterEach(async () => {
    await app?.close();
    await rm(directory, { recursive: true, force: true });
  });

  it('verlangt ein Passwort und fordert den Browser zur Anmeldung auf', async () => {
    await boot(PASSWORD);

    const response = await request(app.getHttpServer()).get('/download');

    expect(response.status).toBe(401);
    // Ohne diesen Header zeigt der Browser kein Anmeldefenster.
    expect(response.headers['www-authenticate']).toContain('Basic');
  });

  it('weist ein falsches Passwort ab', async () => {
    await boot(PASSWORD);

    const response = await request(app.getHttpServer())
      .get('/download')
      .set('Authorization', auth('falsch'));

    expect(response.status).toBe(401);
  });

  it('liefert die Seite mit der Version bei richtigem Passwort', async () => {
    await boot(PASSWORD);

    const response = await request(app.getHttpServer())
      .get('/download')
      .set('Authorization', auth(PASSWORD));

    expect(response.status).toBe(200);
    expect(response.text).toContain('1.1.1');
    expect(response.text).toContain('Build 3');
  });

  it('liefert das APK unter dem Namen mit der Version', async () => {
    await boot(PASSWORD);

    const response = await request(app.getHttpServer())
      .get('/download/app.apk')
      .set('Authorization', auth(PASSWORD));

    expect(response.status).toBe(200);
    expect(response.headers['content-disposition']).toContain('ff-sms-1.1.1.apk');
    expect(response.headers['content-type']).toContain('application/vnd.android.package-archive');
  });

  /**
   * Der wichtigste Fall: ein vergessenes DOWNLOAD_PASSWORD darf das APK nicht
   * offen ins Netz stellen. Kein Passwort heisst "Seite gibt es nicht", nicht
   * "Seite ohne Schutz".
   */
  it('ist ohne gesetztes Passwort gar nicht vorhanden', async () => {
    await boot(null);

    const page = await request(app.getHttpServer()).get('/download');
    const apk = await request(app.getHttpServer()).get('/download/app.apk');

    expect(page.status).toBe(404);
    expect(apk.status).toBe(404);
  });

  it('gibt ohne hinterlegtes APK eine verstaendliche Seite aus', async () => {
    await rm(join(directory, 'app-release.apk'));
    await boot(PASSWORD);

    const response = await request(app.getHttpServer())
      .get('/download')
      .set('Authorization', auth(PASSWORD));

    expect(response.status).toBe(200);
    expect(response.text).toContain('keine Version bereit');
  });
});
