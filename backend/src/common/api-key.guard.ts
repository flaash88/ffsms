import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { timingSafeEqual } from 'node:crypto';
import type { Request } from 'express';
import type { DeviceKey } from '../config/configuration';

/** Der Request traegt nach erfolgreicher Pruefung die Geraete-Kennung. */
export interface AuthenticatedRequest extends Request {
  deviceId?: string;
}

/**
 * Prueft den X-API-Key-Header.
 *
 * Der Schluessel bestimmt die Geraete-Kennung. Ein Geraet kann also keine
 * Daten unter fremder Kennung einliefern, auch wenn es im Body etwas anderes
 * behauptet - der CampaignsController vergleicht device_id gegen den hier
 * gesetzten Wert.
 */
@Injectable()
export class ApiKeyGuard implements CanActivate {
  constructor(private readonly config: ConfigService) {}

  canActivate(context: ExecutionContext): boolean {
    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const provided = request.header('x-api-key');

    if (!provided) {
      throw new UnauthorizedException('X-API-Key fehlt');
    }

    const keys = this.config.get<DeviceKey[]>('deviceKeys') ?? [];
    const match = keys.find((entry) => safeEquals(entry.key, provided));

    if (!match) {
      throw new UnauthorizedException('Unbekannter API-Key');
    }

    request.deviceId = match.deviceId;
    return true;
  }
}

/**
 * Vergleich in konstanter Zeit.
 *
 * Ein naives === wuerde beim ersten abweichenden Zeichen abbrechen und damit
 * ueber die Antwortzeit verraten, wie viele Zeichen stimmen. Bei einem
 * Endpunkt, der aus dem Internet erreichbar ist, ist das vermeidbar.
 */
function safeEquals(expected: string, provided: string): boolean {
  const a = Buffer.from(expected, 'utf8');
  const b = Buffer.from(provided, 'utf8');
  if (a.length !== b.length) {
    // Laengenvergleich trotzdem gegen sich selbst laufen lassen, damit die
    // Dauer nicht von der Laenge abhaengt.
    timingSafeEqual(a, a);
    return false;
  }
  return timingSafeEqual(a, b);
}
