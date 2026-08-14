import { Logger, ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { ConfigService } from '@nestjs/config';
import { AppModule } from './app.module';

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule);

  app.useGlobalPipes(
    new ValidationPipe({
      // Das Herzstueck der Datenschutzzusage:
      // whitelist entfernt alles, was nicht im DTO steht,
      // forbidNonWhitelisted lehnt solche Datensaetze gleich ganz ab.
      // Selbst wenn eine kuenftige App-Version versehentlich Nachrichtentext
      // mitschicken wuerde, landet er nicht in der Datenbank - der Request
      // scheitert mit HTTP 400.
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      transformOptions: { enableImplicitConversion: false },
    }),
  );

  // Kein CORS: die App spricht direkt mit dem Backend, ein Browser-Frontend
  // gibt es nicht. Wer eines ergaenzt, schaltet CORS hier gezielt frei.
  app.enableShutdownHooks();

  const port = app.get(ConfigService).get<number>('port') ?? 3000;
  await app.listen(port, '0.0.0.0');
  new Logger('Bootstrap').log(`FF-SMS-Backend laeuft auf Port ${port}`);
}

void bootstrap();
