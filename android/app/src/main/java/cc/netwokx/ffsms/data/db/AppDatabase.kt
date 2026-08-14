package cc.netwokx.ffsms.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter fun fromCampaignStatus(v: CampaignStatus): String = v.name

    @TypeConverter fun toCampaignStatus(v: String): CampaignStatus = CampaignStatus.valueOf(v)

    @TypeConverter fun fromSendStatus(v: SendStatus): String = v.name

    @TypeConverter fun toSendStatus(v: String): SendStatus = SendStatus.valueOf(v)
}

@Database(
    entities = [
        GroupEntity::class,
        RecipientEntity::class,
        CampaignEntity::class,
        CampaignRecipientEntity::class,
        SendLogEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun groupDao(): GroupDao
    abstract fun recipientDao(): RecipientDao
    abstract fun campaignDao(): CampaignDao
    abstract fun sendLogDao(): SendLogDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * Verknuepfung von Verteilern mit Kontaktgruppen.
         *
         * Echte Migration statt fallbackToDestructiveMigration: auf dem Geraet
         * stehen bereits Verteiler und der Verlauf, und der Verlauf ist der
         * Beleg gegen die Providerrechnung. Der darf bei einem Update nicht
         * verschwinden.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE groups ADD COLUMN contactGroupId INTEGER")
                db.execSQL("ALTER TABLE groups ADD COLUMN contactGroupTitle TEXT")
                db.execSQL("ALTER TABLE groups ADD COLUMN autoSyncContacts INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE groups ADD COLUMN lastContactSyncAt INTEGER")
                db.execSQL("ALTER TABLE recipients ADD COLUMN fromContactGroup INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recipients ADD COLUMN missingInContactGroup INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "ffsms.db",
            )
                // Bewusst ohne fallbackToDestructiveMigration: der Verlauf ist
                // der Beleg gegen die Providerrechnung und darf bei einem
                // Schemawechsel nicht stillschweigend verschwinden.
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
