import { Column, CreateDateColumn, Entity, Index, PrimaryColumn } from 'typeorm';

/**
 * Eine Aussendung - ausschliesslich Verbrauchszahlen.
 *
 * Diese Tabelle enthaelt bewusst keine Spalte fuer Nachrichtentext,
 * Telefonnummern, Kontaktnamen oder Hashes davon. Wer hier eine Spalte
 * ergaenzt, aendert die Datenschutzzusage der App und muss den
 * Datenschutz-Screen sowie die README anpassen.
 */
@Entity('campaigns')
@Index(['deviceId', 'sentAt'])
export class Campaign {
  /**
   * Die UUID der Aussendung, vom Geraet erzeugt.
   * Primaerschluessel: ein wiederholter Upload derselben Aussendung
   * aktualisiert den Datensatz, statt ihn zu verdoppeln.
   */
  @PrimaryColumn({ name: 'campaign_id', type: 'uuid' })
  campaignId!: string;

  @Column({ name: 'device_id', type: 'varchar', length: 64 })
  deviceId!: string;

  @Column({ name: 'sent_at', type: 'timestamptz' })
  sentAt!: Date;

  @Column({ name: 'recipients', type: 'integer' })
  recipients!: number;

  @Column({ name: 'segments_per_msg', type: 'integer' })
  segmentsPerMsg!: number;

  @Column({ name: 'total_segments', type: 'integer' })
  totalSegments!: number;

  /** "GSM7" oder "UCS2". */
  @Column({ name: 'encoding', type: 'varchar', length: 8 })
  encoding!: string;

  @Column({ name: 'failed', type: 'integer', default: 0 })
  failed!: number;

  @Column({ name: 'aborted_reason', type: 'varchar', length: 64, nullable: true })
  abortedReason!: string | null;

  /** Wann der Server den Datensatz bekommen hat - kann deutlich spaeter sein. */
  @CreateDateColumn({ name: 'received_at', type: 'timestamptz' })
  receivedAt!: Date;
}
