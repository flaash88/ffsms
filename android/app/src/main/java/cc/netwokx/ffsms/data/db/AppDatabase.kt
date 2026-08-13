package cc.netwokx.ffsms.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

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
    version = 1,
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

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "ffsms.db",
            )
                // Fremdschluessel MUESSEN aktiv sein: der Verlauf haengt daran,
                // und das Loeschen einer Gruppe darf keine verwaisten
                // Empfaengerzeilen hinterlassen.
                .build()
                .also { instance = it }
        }
    }
}
