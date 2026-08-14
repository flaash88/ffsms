import { Controller, Get, Query, UseGuards } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { ApiKeyGuard } from '../common/api-key.guard';
import { CampaignsService } from '../campaigns/campaigns.service';
import { StatsQueryDto } from './dto/stats-query.dto';
import { monthRange } from '../common/time';

@Controller('api/v1/stats')
@UseGuards(ApiKeyGuard)
export class StatsController {
  constructor(
    private readonly campaigns: CampaignsService,
    private readonly config: ConfigService,
  ) {}

  /**
   * Aggregation ueber einen Zeitraum.
   * Ohne Angabe wird der laufende Kalendermonat in Europe/Vienna genommen -
   * das ist der Zeitraum, der die Providerrechnung bestimmt.
   */
  @Get()
  async stats(@Query() query: StatsQueryDto) {
    const timezone = this.config.get<string>('timezone') ?? 'Europe/Vienna';
    const now = new Date();
    const defaults = monthRange(now, timezone);

    const from = query.from ? new Date(query.from) : defaults.from;
    const to = query.to ? new Date(query.to) : now;
    const deviceId = query.device_id ?? null;

    const campaigns = await this.campaigns.findInRange(deviceId, from, to);

    const byEncoding: Record<string, number> = {};
    for (const campaign of campaigns) {
      byEncoding[campaign.encoding] = (byEncoding[campaign.encoding] ?? 0) + 1;
    }

    return {
      device_id: deviceId,
      from: from.toISOString(),
      to: to.toISOString(),
      campaigns: campaigns.length,
      recipients: campaigns.reduce((sum, c) => sum + c.recipients, 0),
      total_segments: campaigns.reduce((sum, c) => sum + c.totalSegments, 0),
      failed: campaigns.reduce((sum, c) => sum + c.failed, 0),
      aborted: campaigns.filter((c) => c.abortedReason !== null).length,
      by_encoding: byEncoding,
    };
  }
}
