import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { ScheduleModule } from '@nestjs/schedule';
import { TypeOrmModule } from '@nestjs/typeorm';
import configuration from './config/configuration';
import { Campaign } from './campaigns/campaign.entity';
import { CampaignsModule } from './campaigns/campaigns.module';
import { HealthModule } from './health/health.module';
import { NotifyModule } from './notify/notify.module';
import { ReportsModule } from './reports/reports.module';
import { StatsModule } from './stats/stats.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      load: [configuration],
    }),
    ScheduleModule.forRoot(),
    TypeOrmModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        type: 'postgres' as const,
        host: config.get<string>('database.host'),
        port: config.get<number>('database.port'),
        username: config.get<string>('database.username'),
        password: config.get<string>('database.password'),
        database: config.get<string>('database.database'),
        entities: [Campaign],
        // Vertretbar, weil das Schema aus einer einzigen Tabelle besteht und
        // ausschliesslich von dieser Anwendung verwaltet wird. Kaeme eine
        // zweite Tabelle oder ein Feldwechsel dazu, waeren Migrationen faellig.
        synchronize: true,
        logging: ['error', 'warn'],
      }),
    }),
    NotifyModule,
    CampaignsModule,
    StatsModule,
    HealthModule,
    ReportsModule,
  ],
})
export class AppModule {}
