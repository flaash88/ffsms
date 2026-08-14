import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { SchedulerRegistry } from '@nestjs/schedule';
import { CronJob } from 'cron';
import { CampaignsService } from '../campaigns/campaigns.service';
import { NtfyService } from '../notify/ntfy.service';
import { formatWeeklyReport } from './weekly-report';
import {
  isoWeekNumber,
  isoWeekRange,
  monthRange,
  previousMonthRange,
} from '../common/time';

/**
 * Wochenreport, standardmaessig sonntags 20:00 Europe/Vienna.
 *
 * Der Cron-Job wird zur Laufzeit registriert statt per @Cron-Dekorator, damit
 * Ausdruck UND Zeitzone aus der Konfiguration kommen. Eine fest verdrahtete
 * Zeitzone waere hier eine Fehlerquelle: der Unterschied zwischen 20:00 UTC
 * und 20:00 Ortszeit sind im Sommer zwei Stunden, und der Report wuerde in
 * den Montag rutschen.
 */
@Injectable()
export class ReportsService implements OnModuleInit {
  private readonly logger = new Logger(ReportsService.name);

  constructor(
    private readonly campaigns: CampaignsService,
    private readonly ntfy: NtfyService,
    private readonly config: ConfigService,
    private readonly scheduler: SchedulerRegistry,
  ) {}

  onModuleInit(): void {
    const cronExpression = this.config.get<string>('weeklyReportCron') ?? '0 20 * * 0';
    const timezone = this.config.get<string>('timezone') ?? 'Europe/Vienna';

    const job = new CronJob(
      cronExpression,
      () => {
        void this.sendWeeklyReport();
      },
      null,
      false,
      timezone,
    );

    this.scheduler.addCronJob('weekly-report', job);
    job.start();
    this.logger.log(`Wochenreport geplant: "${cronExpression}" (${timezone})`);
  }

  /** Oeffentlich, damit sich der Report auch von Hand ausloesen laesst. */
  async sendWeeklyReport(now: Date = new Date()): Promise<void> {
    const timezone = this.config.get<string>('timezone') ?? 'Europe/Vienna';
    const label = this.config.get<string>('reportTitle') ?? 'FF-SMS';

    const week = isoWeekRange(now, timezone);
    const month = monthRange(now, timezone);
    const previousMonth = previousMonthRange(now, timezone);

    // Ein Report ueber alle Geraete zusammen. Bei mehreren Geraeten waere
    // je Geraet ein eigener Report sinnvoller - dann hier ueber
    // campaigns.distinctDevices() iterieren.
    const weekCampaigns = await this.campaigns.findInRange(null, week.from, week.to);

    const byEncoding: Record<string, number> = {};
    for (const campaign of weekCampaigns) {
      byEncoding[campaign.encoding] = (byEncoding[campaign.encoding] ?? 0) + 1;
    }

    const message = formatWeeklyReport({
      label,
      weekNumber: isoWeekNumber(now, timezone),
      campaigns: weekCampaigns.length,
      recipients: weekCampaigns.reduce((sum, c) => sum + c.recipients, 0),
      totalSegments: weekCampaigns.reduce((sum, c) => sum + c.totalSegments, 0),
      byEncoding,
      monthSoFar: await this.campaigns.sumSegments(null, month.from, month.to),
      previousMonthTotal: await this.campaigns.sumSegments(
        null,
        previousMonth.from,
        previousMonth.to,
      ),
      abortedCount: weekCampaigns.filter((c) => c.abortedReason !== null).length,
      failedCount: weekCampaigns.reduce((sum, c) => sum + c.failed, 0),
    });

    await this.ntfy.send(message);
    this.logger.log(`Wochenreport versendet: ${message.title}`);
  }
}
