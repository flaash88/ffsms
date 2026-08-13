package cc.netwokx.ffsms.app

import android.app.Application
import cc.netwokx.ffsms.notify.Notifications
import cc.netwokx.ffsms.sync.SyncScheduler

class FfSmsApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)

        // Periodischer Upload der Verbrauchszahlen. Der Worker prueft selbst,
        // ob der Sync ueberhaupt eingeschaltet und ein Backend konfiguriert
        // ist - hier wird nur der Zeitplan registriert.
        SyncScheduler.schedulePeriodic(this)
    }
}
