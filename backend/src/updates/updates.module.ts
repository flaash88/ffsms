import { Module } from '@nestjs/common';
import { DownloadController } from './download.controller';
import { UpdatesController } from './updates.controller';
import { UpdatesService } from './updates.service';

@Module({
  controllers: [UpdatesController, DownloadController],
  providers: [UpdatesService],
})
export class UpdatesModule {}
