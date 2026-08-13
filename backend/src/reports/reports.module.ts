import { Module } from '@nestjs/common';
import { CampaignsModule } from '../campaigns/campaigns.module';
import { NotifyModule } from '../notify/notify.module';
import { ReportsService } from './reports.service';

@Module({
  imports: [CampaignsModule, NotifyModule],
  providers: [ReportsService],
})
export class ReportsModule {}
