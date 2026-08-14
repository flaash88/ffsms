import { Controller, HttpCode, HttpStatus, Post, UseGuards } from '@nestjs/common';
import { ApiKeyGuard } from '../common/api-key.guard';
import { ReportsService } from './reports.service';

/**
 * Wochenreport von Hand ausloesen.
 *
 * Gedacht zum Ausprobieren und zum Nachreichen, wenn der Server am Sonntag
 * abend aus war. Sonst waere die einzige Moeglichkeit, den Cron-Ausdruck
 * kurz umzustellen und den Dienst zweimal neu zu starten - was jedes Mal
 * die Gefahr birgt, die Umstellung hinterher zu vergessen und dann jede
 * Minute einen Report zu bekommen.
 *
 * Der Report bezieht sich auf die AKTUELLE Kalenderwoche, nicht auf die
 * vergangene. Von Hand ausgeloest liefert er also den Stand von jetzt.
 */
@Controller('api/v1/reports')
@UseGuards(ApiKeyGuard)
export class ReportsController {
  constructor(private readonly reports: ReportsService) {}

  @Post('weekly')
  @HttpCode(HttpStatus.OK)
  async weekly() {
    await this.reports.sendWeeklyReport();
    return { sent: true };
  }
}
