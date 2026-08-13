import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

export type NtfyPriority = 'min' | 'low' | 'default' | 'high' | 'urgent';

export interface NtfyMessage {
  title: string;
  body: string;
  priority?: NtfyPriority;
  tags?: string[];
}

/**
 * Versendet Meldungen ueber ntfy.
 *
 * Fehler beim Versand werden protokolliert, aber nicht weitergereicht: eine
 * nicht zustellbare Benachrichtigung darf niemals dazu fuehren, dass das
 * Geraet seinen Upload als fehlgeschlagen ansieht und erneut versucht.
 */
@Injectable()
export class NtfyService {
  private readonly logger = new Logger(NtfyService.name);

  constructor(private readonly config: ConfigService) {}

  async send(message: NtfyMessage): Promise<boolean> {
    const url = this.config.get<string | null>('ntfy.url');
    if (!url) {
      this.logger.warn(`NTFY_URL nicht gesetzt, Meldung verworfen: ${message.title}`);
      return false;
    }

    const headers: Record<string, string> = {
      'Content-Type': 'text/plain; charset=utf-8',
      Title: encodeHeader(message.title),
      Priority: message.priority ?? 'default',
    };
    if (message.tags?.length) {
      headers.Tags = message.tags.join(',');
    }

    const token = this.config.get<string | null>('ntfy.token');
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers,
        body: message.body,
      });
      if (!response.ok) {
        this.logger.error(`ntfy antwortete mit ${response.status} ${response.statusText}`);
        return false;
      }
      return true;
    } catch (error) {
      this.logger.error(`ntfy nicht erreichbar: ${(error as Error).message}`);
      return false;
    }
  }
}

/**
 * HTTP-Header duerfen nur Latin-1 enthalten. Umlaute im Titel wuerden den
 * Request sonst zerlegen, deshalb werden sie ersetzt statt weggeworfen.
 */
function encodeHeader(value: string): string {
  return value
    .replace(/ä/g, 'ae')
    .replace(/ö/g, 'oe')
    .replace(/ü/g, 'ue')
    .replace(/Ä/g, 'Ae')
    .replace(/Ö/g, 'Oe')
    .replace(/Ü/g, 'Ue')
    .replace(/ß/g, 'ss')
    // eslint-disable-next-line no-control-regex
    .replace(/[^\x20-\x7E]/g, '');
}
