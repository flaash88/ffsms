import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectRepository } from '@nestjs/typeorm';
import { Between, Repository } from 'typeorm';
import { Campaign } from './campaign.entity';
import { CreateCampaignDto } from './dto/create-campaign.dto';
import { NtfyService } from '../notify/ntfy.service';
import { evaluateAlert } from '../notify/alerts';
import { monthRange } from '../common/time';

export interface IngestResult {
  campaignId: string;
  duplicate: boolean;
}

@Injectable()
export class CampaignsService {
  private readonly logger = new Logger(CampaignsService.name);

  constructor(
    @InjectRepository(Campaign)
    private readonly repo: Repository<Campaign>,
    private readonly ntfy: NtfyService,
    private readonly config: ConfigService,
  ) {}

  /**
   * Nimmt eine Aussendung entgegen.
   *
   * Idempotent ueber die campaign_id: ein wiederholter Upload aktualisiert
   * den Datensatz und loest KEINEN erneuten Alarm aus. Das Geraet laedt
   * offene Eintraege erneut hoch, wenn es zwischendurch offline war - daraus
   * darf kein Alarmgewitter entstehen.
   */
  async ingest(dto: CreateCampaignDto): Promise<IngestResult> {
    const existing = await this.repo.findOne({ where: { campaignId: dto.campaign_id } });

    const entity = this.repo.create({
      campaignId: dto.campaign_id,
      deviceId: dto.device_id,
      sentAt: new Date(dto.sent_at),
      recipients: dto.recipients,
      segmentsPerMsg: dto.segments_per_msg,
      totalSegments: dto.total_segments,
      encoding: dto.encoding,
      failed: dto.failed,
      abortedReason: dto.aborted_reason ?? null,
    });

    await this.repo.upsert(entity, ['campaignId']);

    if (existing) {
      this.logger.log(`Erneuter Upload von ${dto.campaign_id}, kein Alarm.`);
      return { campaignId: dto.campaign_id, duplicate: true };
    }

    await this.maybeAlert(entity);
    return { campaignId: dto.campaign_id, duplicate: false };
  }

  private async maybeAlert(campaign: Campaign): Promise<void> {
    const timezone = this.config.get<string>('timezone') ?? 'Europe/Vienna';
    const { from, to } = monthRange(campaign.sentAt, timezone);
    const monthTotal = await this.sumSegments(campaign.deviceId, from, to);

    const alert = evaluateAlert(
      {
        deviceId: campaign.deviceId,
        totalSegments: campaign.totalSegments,
        recipients: campaign.recipients,
        segmentsPerMsg: campaign.segmentsPerMsg,
        encoding: campaign.encoding,
        failed: campaign.failed,
        abortedReason: campaign.abortedReason,
        monthTotal,
      },
      {
        segmentsPerCampaign:
          this.config.get<number>('ntfy.alertSegmentsPerCampaign') ?? 150,
        segmentsPerMonth: this.config.get<number>('ntfy.alertSegmentsPerMonth') ?? 1500,
      },
    );

    if (alert) {
      await this.ntfy.send(alert);
    }
  }

  async sumSegments(deviceId: string | null, from: Date, to: Date): Promise<number> {
    const query = this.repo
      .createQueryBuilder('c')
      .select('COALESCE(SUM(c.total_segments), 0)', 'sum')
      .where('c.sent_at >= :from AND c.sent_at < :to', { from, to });

    if (deviceId) {
      query.andWhere('c.device_id = :deviceId', { deviceId });
    }

    const result = await query.getRawOne<{ sum: string }>();
    return Number.parseInt(result?.sum ?? '0', 10);
  }

  async findInRange(
    deviceId: string | null,
    from: Date,
    to: Date,
  ): Promise<Campaign[]> {
    return this.repo.find({
      where: deviceId
        ? { deviceId, sentAt: Between(from, to) }
        : { sentAt: Between(from, to) },
      order: { sentAt: 'ASC' },
    });
  }

  async distinctDevices(): Promise<string[]> {
    const rows = await this.repo
      .createQueryBuilder('c')
      .select('DISTINCT c.device_id', 'deviceId')
      .getRawMany<{ deviceId: string }>();
    return rows.map((row) => row.deviceId);
  }
}
