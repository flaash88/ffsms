package cc.netwokx.ffsms.app

import android.content.Context
import cc.netwokx.ffsms.data.db.AppDatabase
import cc.netwokx.ffsms.data.repo.CampaignRepository
import cc.netwokx.ffsms.data.repo.GroupRepository
import cc.netwokx.ffsms.data.settings.SettingsRepository

/**
 * Minimale manuelle Abhaengigkeitsaufloesung.
 *
 * Bewusst kein Hilt/Dagger: die App hat eine Handvoll Singletons, und ein
 * Annotation-Processor mehr macht den Build fuer eine Feuerwehr, die die APK
 * vielleicht in zwei Jahren neu bauen muss, nur zerbrechlicher.
 */
object ServiceLocator {

    @Volatile private var settingsRepo: SettingsRepository? = null
    @Volatile private var groupRepo: GroupRepository? = null
    @Volatile private var campaignRepo: CampaignRepository? = null

    fun database(context: Context): AppDatabase = AppDatabase.get(context)

    fun settings(context: Context): SettingsRepository = settingsRepo ?: synchronized(this) {
        settingsRepo ?: SettingsRepository(context.applicationContext).also { settingsRepo = it }
    }

    fun groups(context: Context): GroupRepository = groupRepo ?: synchronized(this) {
        groupRepo ?: run {
            val db = database(context)
            GroupRepository(db.groupDao(), db.recipientDao()).also { groupRepo = it }
        }
    }

    fun campaigns(context: Context): CampaignRepository = campaignRepo ?: synchronized(this) {
        campaignRepo ?: run {
            val db = database(context)
            CampaignRepository(
                context = context.applicationContext,
                campaignDao = db.campaignDao(),
                sendLogDao = db.sendLogDao(),
            ).also { campaignRepo = it }
        }
    }
}
