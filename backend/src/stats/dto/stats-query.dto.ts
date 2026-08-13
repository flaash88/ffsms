import { IsISO8601, IsOptional, IsString, Matches } from 'class-validator';

export class StatsQueryDto {
  @IsOptional()
  @IsString()
  @Matches(/^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$/)
  device_id?: string;

  /** Beginn des Zeitraums, einschliesslich. Fehlt der Wert: Monatsanfang. */
  @IsOptional()
  @IsISO8601({ strict: true })
  from?: string;

  /** Ende des Zeitraums, ausschliesslich. Fehlt der Wert: jetzt. */
  @IsOptional()
  @IsISO8601({ strict: true })
  to?: string;
}
