import {
  IsIn,
  IsISO8601,
  IsInt,
  IsOptional,
  IsString,
  IsUUID,
  Matches,
  Max,
  MaxLength,
  Min,
} from 'class-validator';

/**
 * Das einzige akzeptierte Eingabeformat.
 *
 * Die globale ValidationPipe laeuft mit whitelist und forbidNonWhitelisted:
 * ein Feld, das hier nicht steht, fuehrt zu HTTP 400. Das ist die technische
 * Absicherung der Datenschutzzusage - selbst wenn eine kuenftige App-Version
 * versehentlich Text mitschicken wuerde, nimmt der Server ihn nicht an,
 * sondern lehnt den gesamten Datensatz ab.
 *
 * Die Feldnamen sind snake_case, weil sie 1:1 dem JSON entsprechen.
 */
export class CreateCampaignDto {
  @IsString()
  @Matches(/^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$/, {
    message: 'device_id darf nur Kleinbuchstaben, Ziffern und Bindestriche enthalten',
  })
  device_id!: string;

  @IsUUID()
  campaign_id!: string;

  @IsISO8601({ strict: true })
  sent_at!: string;

  @IsInt()
  @Min(0)
  @Max(100000)
  recipients!: number;

  @IsInt()
  @Min(0)
  @Max(1000)
  segments_per_msg!: number;

  @IsInt()
  @Min(0)
  @Max(1000000)
  total_segments!: number;

  @IsIn(['GSM7', 'UCS2'])
  encoding!: string;

  @IsInt()
  @Min(0)
  @Max(100000)
  failed!: number;

  /**
   * Grund eines Abbruchs, z. B. "limit_daily". Kurz gehalten und auf ein
   * enges Zeichenmuster begrenzt, damit das Feld nicht als Umweg fuer
   * freien Text taugt.
   */
  @IsOptional()
  @IsString()
  @MaxLength(64)
  @Matches(/^[a-z0-9_]+$/, {
    message: 'aborted_reason darf nur Kleinbuchstaben, Ziffern und Unterstriche enthalten',
  })
  aborted_reason?: string | null;
}
