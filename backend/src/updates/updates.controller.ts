import { Controller, Get, Header, StreamableFile, UseGuards } from '@nestjs/common';
import { ApiKeyGuard } from '../common/api-key.guard';
import { UpdatesService } from './updates.service';

/**
 * Update-Kanal fuer die App.
 *
 * Hinter dem API-Key, wie alles andere auch: das APK enthaelt zwar keine
 * Geheimnisse, aber es gibt keinen Grund, es offen ins Netz zu haengen.
 */
@Controller('api/v1/update')
@UseGuards(ApiKeyGuard)
export class UpdatesController {
  constructor(private readonly updates: UpdatesService) {}

  /** Was liegt bereit? Die App vergleicht version_code mit ihrer eigenen. */
  @Get('manifest')
  manifest() {
    return this.updates.manifest();
  }

  @Get('apk')
  @Header('Content-Type', 'application/vnd.android.package-archive')
  @Header('Content-Disposition', 'attachment; filename="app-release.apk"')
  async apk(): Promise<StreamableFile> {
    return new StreamableFile(await this.updates.apkStream());
  }
}
