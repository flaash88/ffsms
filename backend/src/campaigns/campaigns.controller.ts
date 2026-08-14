import {
  Body,
  Controller,
  ForbiddenException,
  HttpCode,
  HttpStatus,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import { ApiKeyGuard, type AuthenticatedRequest } from '../common/api-key.guard';
import { CampaignsService } from './campaigns.service';
import { CreateCampaignDto } from './dto/create-campaign.dto';

@Controller('api/v1/campaigns')
@UseGuards(ApiKeyGuard)
export class CampaignsController {
  constructor(private readonly service: CampaignsService) {}

  /**
   * Nimmt eine Aussendung entgegen.
   *
   * 200 statt 201, weil der Aufruf idempotent ist: derselbe Datensatz darf
   * beliebig oft geschickt werden. Die Antwort sagt ueber "duplicate", ob es
   * ein Neuzugang war.
   */
  @Post()
  @HttpCode(HttpStatus.OK)
  async create(@Body() dto: CreateCampaignDto, @Req() request: AuthenticatedRequest) {
    // Der API-Key bestimmt die Geraete-Kennung. Weicht der Body davon ab,
    // versucht ein Geraet unter fremdem Namen einzuliefern.
    if (request.deviceId !== dto.device_id) {
      throw new ForbiddenException(
        `Der verwendete API-Key gehoert nicht zu device_id "${dto.device_id}"`,
      );
    }

    const result = await this.service.ingest(dto);
    return {
      campaign_id: result.campaignId,
      duplicate: result.duplicate,
    };
  }
}
