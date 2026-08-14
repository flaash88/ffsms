import { Module } from '@nestjs/common';
import { CampaignsModule } from '../campaigns/campaigns.module';
import { StatsController } from './stats.controller';

@Module({
  imports: [CampaignsModule],
  controllers: [StatsController],
})
export class StatsModule {}
