package com.lifeline.app

import android.app.Application
import com.lifeline.app.ui.theme.ThemePreferenceManager

/**
 * Main application class for LifeLine.
 */
class LifeLineApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Start the single process-wide power policy before transport components are constructed.
        com.lifeline.app.mesh.PowerManager.getInstance(this).start()

        // Restore private conversations before background transports can deliver new messages.
        // AppStateStore merges any in-flight arrivals by message ID, so startup cannot replace
        // newer transport state with an older database snapshot.
        try {
            com.lifeline.app.services.AppStateStore.initializeConversationPersistence(this)
        } catch (_: Exception) { }

        // Initialize theme preference
        ThemePreferenceManager.init(this)

        // Initialize debug preference manager (persists debug toggles)
        try { com.lifeline.app.ui.debug.DebugPreferenceManager.init(this) } catch (_: Exception) { }

        // Initialize Wi‑Fi Aware controller with persisted default
        try {
            val enabled = com.lifeline.app.ui.debug.DebugPreferenceManager.getWifiAwareEnabled(false)
            com.lifeline.app.wifiaware.WifiAwareController.initialize(this, enabled)
        } catch (_: Exception) { }

        // Initialize mesh service preferences
        try { com.lifeline.app.service.MeshServicePreferences.init(this) } catch (_: Exception) { }

        // Listen for SOS alerts before the mesh starts, so alerts surface even with the UI closed
        try { com.lifeline.app.emergency.EmergencyRuntime.initialize(this) } catch (_: Exception) { }

        // Proactively start the foreground service to keep mesh alive
        try { com.lifeline.app.service.MeshForegroundService.start(this) } catch (_: Exception) { }
    }
}
