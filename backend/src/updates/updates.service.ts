import { Injectable, Logger, NotFoundException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createHash } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { readFile, stat } from 'node:fs/promises';
import { join } from 'node:path';
import { Readable } from 'node:stream';

export interface UpdateManifest {
  version_code: number;
  version_name: string;
  size_bytes: number;
  sha256: string;
  notes: string | null;
  released_at: string;
}

interface ReleaseJson {
  version_code: number;
  version_name: string;
  notes?: string;
}

/**
 * Stellt die aktuelle APK-Version bereit.
 *
 * Das Verzeichnis enthaelt zwei Dateien:
 *   app-release.apk  - das signierte APK
 *   release.json     - { "version_code": 2, "version_name": "1.0.1", "notes": "..." }
 *
 * Der SHA-256 wird vom Server berechnet und nicht aus release.json gelesen:
 * so kann eine unvollstaendig hochgeladene Datei nicht als gueltig ausgewiesen
 * werden. Die App prueft die Pruefsumme nach dem Download erneut und bricht
 * bei Abweichung ab.
 */
@Injectable()
export class UpdatesService {
  private readonly logger = new Logger(UpdatesService.name);
  private cache: { key: string; manifest: UpdateManifest } | null = null;

  constructor(private readonly config: ConfigService) {}

  private get directory(): string {
    return this.config.get<string>('updateDir') ?? '/data/updates';
  }

  get apkPath(): string {
    return join(this.directory, 'app-release.apk');
  }

  private get releasePath(): string {
    return join(this.directory, 'release.json');
  }

  async manifest(): Promise<UpdateManifest> {
    const info = await stat(this.apkPath).catch(() => null);
    if (!info) {
      throw new NotFoundException(
        'Kein Update hinterlegt. Erwartet wird app-release.apk im Update-Verzeichnis.',
      );
    }

    // Groesse und Aenderungszeit als Cache-Schluessel: der SHA-256 einer
    // 20-MB-Datei bei jeder Abfrage neu zu berechnen waere Verschwendung.
    const key = `${info.size}:${info.mtimeMs}`;
    if (this.cache?.key === key) {
      return this.cache.manifest;
    }

    const release = await this.readReleaseJson();
    const manifest: UpdateManifest = {
      version_code: release.version_code,
      version_name: release.version_name,
      size_bytes: info.size,
      sha256: await this.sha256(this.apkPath),
      notes: release.notes?.trim() || null,
      released_at: info.mtime.toISOString(),
    };

    this.cache = { key, manifest };
    this.logger.log(
      `Update bereit: ${manifest.version_name} (${manifest.version_code}), ${manifest.size_bytes} Bytes`,
    );
    return manifest;
  }

  async apkStream(): Promise<Readable> {
    const exists = await stat(this.apkPath).catch(() => null);
    if (!exists) {
      throw new NotFoundException('Kein Update hinterlegt.');
    }
    return createReadStream(this.apkPath);
  }

  private async readReleaseJson(): Promise<ReleaseJson> {
    const raw = await readFile(this.releasePath, 'utf8').catch(() => null);
    if (!raw) {
      throw new NotFoundException(
        'release.json fehlt im Update-Verzeichnis.',
      );
    }

    let parsed: unknown;
    try {
      parsed = JSON.parse(raw);
    } catch {
      throw new NotFoundException('release.json ist kein gueltiges JSON.');
    }

    const value = parsed as Partial<ReleaseJson>;
    if (
      typeof value.version_code !== 'number' ||
      !Number.isInteger(value.version_code) ||
      value.version_code < 1 ||
      typeof value.version_name !== 'string' ||
      value.version_name.trim() === ''
    ) {
      throw new NotFoundException(
        'release.json braucht version_code (ganze Zahl >= 1) und version_name.',
      );
    }

    return {
      version_code: value.version_code,
      version_name: value.version_name.trim(),
      notes: typeof value.notes === 'string' ? value.notes : undefined,
    };
  }

  private sha256(path: string): Promise<string> {
    return new Promise((resolve, reject) => {
      const hash = createHash('sha256');
      const stream = createReadStream(path);
      stream.on('data', (chunk) => hash.update(chunk));
      stream.on('error', reject);
      stream.on('end', () => resolve(hash.digest('hex')));
    });
  }
}
