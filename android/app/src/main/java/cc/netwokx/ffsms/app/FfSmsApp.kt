package cc.netwokx.ffsms.app

import android.app.Application
import cc.netwokx.ffsms.notify.Notifications
import cc.netwokx.ffsms.sync.ContactSyncScheduler
import cc.netwokx.ffsms.sync.SyncScheduler
import cc.netwokx.ffsms.update.UpdateScheduler

class FfSmsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)

        // Periodischer Upload der Verbrauchszahlen. Der Worker prueft selbst,
        // ob der Sync ueberhaupt eingeschaltet und ein Backend konfiguriert
        // ist - hier wird nur der Zeitplan registriert.
        SyncScheduler.schedulePeriodic(this)

        // Taegliche Suche nach einer neueren Version. Meldet nur, installiert
        // nichts von allein.
        UpdateScheduler.schedulePeriodic(this)

        // Taeglicher Abgleich der Verteiler mit ihren Kontaktgruppen. Der
        // Worker prueft selbst, ob ueberhaupt eine Verknuepfung besteht.
        ContactSyncScheduler.schedulePeriodic(this)
    }
}
