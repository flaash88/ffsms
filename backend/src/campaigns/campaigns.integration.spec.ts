import { INestApplication, ValidationPipe } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import request from 'supertest';
import { Campaign } from './campaign.entity';
import { CampaignsController } from './campaigns.controller';
import { CampaignsService } from './campaigns.service';
import { NtfyService, type NtfyMessage } from '../notify/ntfy.service';

const DEVICE = 'ff-kuehwiesen-sms';
const KEY = 'testschluessel-1234567890abcdef';

/**
 * Minimales Repository im Speicher.
 * Reicht fuer die Pfade, die der CampaignsService tatsaechlich nutzt.
 */
class FakeCampaignRepository {
  readonly rows = new Map<string, Campaign>();

  create(data: Partial<Campaign>): Campaign {
    return data as Campaign;
  }

  async findOne(options: { where: { campaignId: string } }): Promise<Campaign | null> {
    return this.rows.get(options.where.campaignId) ?? null;
  }

  async upsert(entity: Campaign): Promise<void> {
    this.rows.set(entity.campaignId, entity);
  }

  createQueryBuilder() {
    const rows = [...this.rows.values()];
    const builder = {
      select: () => builder,
      where: () => builder,
      andWhere: () => builder,
      getRawOne: async () => ({
        sum: String(rows.reduce((sum, row) => sum + row.totalSegments, 0)),
      }),
      getRawMany: async () => [],
    };
    return builder;
  }
}

function payload(overrides: Record<string, unknown> = {}) {
  return {
    device_id: DEVICE,
    campaign_id: '3f2504e0-4f89-11d3-9a0c-0305e82c3301',
    sent_at: '2026-08-13T18:22:00+02:00',
    recipients: 15,
    segments_per_msg: 3,
    total_segments: 45,
    encoding: 'GSM7',
    failed: 0,
    aborted_reason: null,
    ...overrides,
  };
}

describe('POST /api/v1/campaigns', () => {
  let app: INestApplication;
  let repo: FakeCampaignRepository;
  let sentMessages: NtfyMessage[];

  beforeEach(async () => {
    repo = new FakeCampaignRepository();
    sentMessages = [];

    const moduleRef = await Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({
          isGlobal: true,
          load: [
            () => ({
              deviceKeys: [{ deviceId: DEVICE, key: KEY }],
              timezone: 'Europe/Vienna',
              ntfy: { alertSegmentsPerCampaign: 150, alertSegmentsPerMonth: 1500 },
            }),
          ],
        }),
      ],
      controllers: [CampaignsController],
      providers: [
        CampaignsService,
        { provide: getRepositoryToken(Campaign), useValue: repo },
        {
          provide: NtfyService,
          useValue: {
            send: async (message: NtfyMessage) => {
              sentMessages.push(message);
              return true;
            },
          },
        },
      ],
    }).compile();

    app = moduleRef.createNestApplication();
    // Dieselbe Pipe wie in main.ts - sie ist Teil dessen, was hier geprueft wird.
    app.useGlobalPipes(
      new ValidationPipe({
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
        transformOptions: { enableImplicitConversion: false },
      }),
    );
    await app.init();
  });

  afterEach(async () => {
    await app.close();
  });

  const post = (body: object, key: string | null = KEY) => {
    const req = request(app.getHttpServer()).post('/api/v1/campaigns');
    return key === null ? req.send(body) : req.set('X-API-Key', key).send(body);
  };

  describe('Authentifizierung', () => {
    it('lehnt Anfragen ohne Schluessel ab', async () => {
      await post(payload(), null).expect(401);
    });

    it('lehnt einen falschen Schluessel ab', async () => {
      await post(payload(), 'falscher-schluessel-aber-gleiche-laenge').expect(401);
    });

    it('lehnt eine fremde Geraete-Kennung ab', async () => {
      // Gueltiger Schluessel, aber der Body behauptet ein anderes Geraet.
      await post(payload({ device_id: 'fremdes-geraet' })).expect(403);
    });
  });

  describe('Datenschutz', () => {
    it('lehnt einen Datensatz mit Nachrichtentext komplett ab', async () => {
      // Der Kern der Zusage: selbst wenn eine App-Version versehentlich Text
      // mitschickt, landet er nicht in der Datenbank.
      const response = await post(payload({ message_text: 'Alarm Kuehwiesen' })).expect(400);
      expect(JSON.stringify(response.body)).toContain('message_text');
      expect(repo.rows.size).toBe(0);
    });

    it('lehnt Telefonnummern ab', async () => {
      await post(payload({ recipients_msisdn: ['+436641234567'] })).expect(400);
      expect(repo.rows.size).toBe(0);
    });

    it('lehnt einen Hash des Textes ab', async () => {
      await post(payload({ text_hash: 'a3f5c9' })).expect(400);
      expect(repo.rows.size).toBe(0);
    });
  });

  describe('Validierung', () => {
    it('nimmt einen korrekten Datensatz an', async () => {
      const response = await post(payload()).expect(200);
      expect(response.body).toEqual({
        campaign_id: '3f2504e0-4f89-11d3-9a0c-0305e82c3301',
        duplicate: false,
      });
      expect(repo.rows.size).toBe(1);
    });

    it('lehnt ein unbekanntes Encoding ab', async () => {
      await post(payload({ encoding: 'UTF8' })).expect(400);
    });

    it('lehnt eine campaign_id ab, die keine UUID ist', async () => {
      await post(payload({ campaign_id: 'kampagne-1' })).expect(400);
    });

    it('lehnt negative Zahlen ab', async () => {
      await post(payload({ recipients: -1 })).expect(400);
    });

    it('lehnt freien Text im Abbruchgrund ab', async () => {
      await post(payload({ aborted_reason: 'Abbruch weil Text zu lang war' })).expect(400);
    });

    it('nimmt einen bekannten Abbruchgrund an', async () => {
      await post(payload({ aborted_reason: 'limit_daily' })).expect(200);
    });
  });

  describe('Idempotenz', () => {
    it('zaehlt einen wiederholten Upload nicht doppelt', async () => {
      const first = await post(payload()).expect(200);
      expect(first.body.duplicate).toBe(false);

      const second = await post(payload()).expect(200);
      expect(second.body.duplicate).toBe(true);

      expect(repo.rows.size).toBe(1);
    });

    it('alarmiert bei einem unveraenderten wiederholten Upload kein zweites Mal', async () => {
      // Das Geraet laedt offene Eintraege nach einer Offline-Phase erneut
      // hoch. Daraus darf kein Alarmgewitter werden.
      await post(payload({ total_segments: 400 })).expect(200);
      expect(sentMessages).toHaveLength(1);

      await post(payload({ total_segments: 400 })).expect(200);
      expect(sentMessages).toHaveLength(1);
    });

    it('alarmiert, wenn ein Nachtrag Fehlschlaege meldet', async () => {
      // Der Regelfall, nicht der Sonderfall: beim Absetzen weiss das Geraet
      // noch nicht, ob eine Nachricht ankommt. Der erste Upload meldet
      // deshalb fast immer "failed: 0", der zweite die Wahrheit. Ohne diese
      // Ausnahme koennte der Alarm bei Fehlschlaegen nie ausloesen.
      await post(payload({ failed: 0 })).expect(200);
      expect(sentMessages).toHaveLength(0);

      const nachtrag = await post(payload({ failed: 15 })).expect(200);
      expect(nachtrag.body.duplicate).toBe(true);
      expect(sentMessages).toHaveLength(1);
      expect(sentMessages[0].body).toContain('15');
    });

    it('alarmiert, wenn ein Nachtrag einen Abbruch meldet', async () => {
      await post(payload()).expect(200);
      expect(sentMessages).toHaveLength(0);

      await post(payload({ aborted_reason: 'limit_daily' })).expect(200);
      expect(sentMessages).toHaveLength(1);
    });

    it('schweigt, wenn die Fehlerzahl sinkt', async () => {
      // Ein erfolgreicher Neuversuch von Hand. Gute Nachrichten brauchen
      // keinen Alarm.
      await post(payload({ failed: 3 })).expect(200);
      expect(sentMessages).toHaveLength(1);

      await post(payload({ failed: 0 })).expect(200);
      expect(sentMessages).toHaveLength(1);
    });
  });

  describe('Sofort-Alarm', () => {
    it('schweigt bei einer unauffaelligen Aussendung', async () => {
      await post(payload()).expect(200);
      expect(sentMessages).toHaveLength(0);
    });

    it('meldet eine grosse Aussendung', async () => {
      await post(payload({ total_segments: 2100 })).expect(200);
      expect(sentMessages).toHaveLength(1);
      expect(sentMessages[0].priority).toBe('high');
      expect(sentMessages[0].title).toContain('2100');
    });

    it('meldet einen Abbruch', async () => {
      await post(payload({ aborted_reason: 'limit_campaign' })).expect(200);
      expect(sentMessages[0].title).toContain('ABGEBROCHEN');
    });

    it('meldet fehlgeschlagene Empfaenger', async () => {
      await post(payload({ failed: 3 })).expect(200);
      expect(sentMessages[0].title).toContain('3 Empfaenger');
    });
  });
});
