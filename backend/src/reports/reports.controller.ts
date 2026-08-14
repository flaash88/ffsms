import {
  Controller,
  HttpCode,
  HttpException,
  HttpStatus,
  Post,
  UseGuards,
} from '@nestjs/common';
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

  /**
   * Antwortet nur dann mit 200, wenn ntfy die Meldung angenommen hat.
   *
   * Ein bedingungsloses {"sent":true} waere hier die schlechtere Wahl: wer
   * den Aufruf absetzt, steht daneben und wartet auf das Handy. Bleibt es
   * still, muss die Antwort sagen, dass der Versand gescheitert ist - sonst
   * sucht man den Fehler beim Handy, waehrend er beim ntfy-Token liegt.
   * Der Grund steht im Log des api-Containers.
   */
  @Post('weekly')
  @HttpCode(HttpStatus.OK)
  async weekly() {
    const sent = await this.reports.sendWeeklyReport();
    if (!sent) {
      throw new HttpException(
        {
          sent: false,
          message:
            'ntfy hat die Meldung nicht angenommen. Grund siehe Log: ' +
            'docker compose logs api | grep -i ntfy',
        },
        HttpStatus.BAD_GATEWAY,
      );
    }
    return { sent: true };
  }
}
