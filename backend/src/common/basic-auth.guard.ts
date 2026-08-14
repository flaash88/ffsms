import {
  CanActivate,
  ExecutionContext,
  Injectable,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { timingSafeEqual } from 'node:crypto';
import type { Request, Response } from 'express';

/**
 * HTTP-Basic-Auth fuer die Download-Seite.
 *
 * Anders als beim API-Key sitzt hier ein Mensch mit einem Browser davor. Der
 * kann keinen Header setzen, wohl aber ein Passwort eintippen - und Basic Auth
 * ist die einzige Art von Anmeldung, die jeder Browser von sich aus anbietet,
 * ohne dass es dafuer eine Anmeldeseite, ein Cookie und eine Sitzungsverwaltung
 * braucht. Fuer "ein Passwort, das ich dem Kollegen per Telefon durchsage" ist
 * das die passende Groesse.
 *
 * Der Benutzername wird nicht geprueft. Es gibt nur ein Passwort, und ein
 * zweites Feld, das niemand kennt, waere kein zusaetzlicher Schutz - nur eine
 * weitere Sache, die man dem Kollegen durchsagen und die er falsch eintippen
 * kann.
 */
@Injectable()
export class BasicAuthGuard implements CanActivate {
  constructor(private readonly config: ConfigService) {}

  canActivate(context: ExecutionContext): boolean {
    const password = this.config.get<string | null>('downloadPassword');

    // Ohne gesetztes Passwort gibt es die Seite nicht - sie ist nicht etwa
    // offen. Ein vergessener Eintrag in der .env darf das APK nicht
    // ungeschuetzt ins Netz stellen, und ein 404 verraet nebenbei nicht
    // einmal, dass es hier ueberhaupt etwas gaebe.
    if (!password) {
      throw new NotFoundException();
    }

    const request = context.switchToHttp().getRequest<Request>();
    const header = request.header('authorization');
    const provided = header?.startsWith('Basic ')
      ? decodePassword(header.slice('Basic '.length))
      : null;

    if (provided !== null && safeEquals(password, provided)) {
      return true;
    }

    // Ohne diesen Header zeigt der Browser kein Anmeldefenster, sondern nur
    // eine leere Fehlerseite.
    const response = context.switchToHttp().getResponse<Response>();
    response.setHeader('WWW-Authenticate', 'Basic realm="FF-SMS", charset="UTF-8"');
    throw new UnauthorizedException('Passwort erforderlich');
  }
}

/** "benutzer:passwort" in Base64. Alles nach dem ersten Doppelpunkt zaehlt. */
function decodePassword(encoded: string): string | null {
  let decoded: string;
  try {
    decoded = Buffer.from(encoded, 'base64').toString('utf8');
  } catch {
    return null;
  }
  const separator = decoded.indexOf(':');
  return separator === -1 ? null : decoded.slice(separator + 1);
}

/** Vergleich in konstanter Zeit, siehe ApiKeyGuard. */
function safeEquals(expected: string, provided: string): boolean {
  const a = Buffer.from(expected, 'utf8');
  const b = Buffer.from(provided, 'utf8');
  if (a.length !== b.length) {
    timingSafeEqual(a, a);
    return false;
  }
  return timingSafeEqual(a, b);
}
