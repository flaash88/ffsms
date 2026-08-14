import { Controller, Get, UseGuards } from '@nestjs/common';
import { InjectDataSource } from '@nestjs/typeorm';
import { DataSource } from 'typeorm';
import { ApiKeyGuard } from '../common/api-key.guard';

@Controller('api/v1/health')
export class HealthController {
  constructor(@InjectDataSource() private readonly dataSource: DataSource) {}

  /**
   * Erreichbarkeit UND Gueltigkeit des API-Keys.
   *
   * Der Endpunkt liegt bewusst hinter dem Guard: die App nutzt ihn fuer
   * "Verbindung testen", und ein Test, der auch mit falschem Schluessel
   * gruen meldet, waere wertlos. Fuer externes Uptime-Monitoring ohne
   * Schluessel gibt es /api/v1/health/live.
   */
  @Get()
  @UseGuards(ApiKeyGuard)
  async health() {
    const databaseUp = await this.dataSource
      .query('SELECT 1')
      .then(() => true)
      .catch(() => false);

    return {
      status: databaseUp ? 'ok' : 'degraded',
      database: databaseUp ? 'up' : 'down',
      version: process.env.npm_package_version ?? null,
    };
  }

  /** Schlanker Endpunkt fuer Uptime-Monitoring, ohne Authentifizierung. */
  @Get('live')
  live() {
    return { status: 'ok' };
  }
}
