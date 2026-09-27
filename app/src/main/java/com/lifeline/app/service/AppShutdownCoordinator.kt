package com.lifeline.app.service

import android.app.Application
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import com.lifeline.app.mesh.MeshService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/**
 * Coordinates a full application shutdown:
 * - Stop mesh cleanly
 * - Clear in-memory AppState
 * - Stop foreground service/notification
 * - Kill the process after completion or after a 5s timeout
 */
object AppShutdownCoordinator {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val shutdownToken = AtomicLong(0L)
    @Volatile
    private var shutdownJob: Job? = null

    fun cancelPendingShutdown() {
        shutdownToken.incrementAndGet()
        shutdownJob?.cancel()
        shutdownJob = null
    }

    fun requestFullShutdownAndKill(
        app: Application,
        mesh: MeshService?,
        notificationManager: NotificationManagerCompat,
        stopForeground: () -> Unit,
        stopService: () -> Unit
    ) {
        val token = shutdownToken.incrementAndGet()
        shutdownJob?.cancel()
        val job = scope.launch {
            // Signal UI to finish gracefully before we kill the process
            try {
                val intent = android.content.Intent(com.lifeline.app.util.AppConstants.UI.ACTION_FORCE_FINISH)
                    .setPackage(app.packageName)
                app.sendBroadcast(intent, com.lifeline.app.util.AppConstants.UI.PERMISSION_FORCE_FINISH)
            } catch (_: Exception) { }

            // Stop mesh (best-effort)
            try { mesh?.stopServices() } catch (_: Exception) { }
            try { com.lifeline.app.mesh.PowerManager.getInstance(app).shutdown() } catch (_: Exception) { }

            val conversationFlush = async {
                try {
                    com.lifeline.app.services.AppStateStore
                        .awaitConversationPersistence()
                } catch (_: Exception) { }
            }

            // Clear AppState in-memory store
            try { com.lifeline.app.services.AppStateStore.clear() } catch (_: Exception) { }

            // Stop foreground and clear notification
            try { stopForeground() } catch (_: Exception) { }
            try { notificationManager.cancel(10001) } catch (_: Exception) { }

            // Wait up to 5 seconds for shutdown tasks
            withTimeoutOrNull(5000) {
                try { conversationFlush.await() } catch (_: Exception) { }
                delay(100)
            }

            // Stop the service itself
            if (!isActive || shutdownToken.get() != token) return@launch
            try { stopService() } catch (_: Exception) { }

            // Hard kill the app process
            if (!isActive || shutdownToken.get() != token) return@launch
            try { Process.killProcess(Process.myPid()) } catch (_: Exception) { }
            try { System.exit(0) } catch (_: Exception) { }
        }
        shutdownJob = job
        job.invokeOnCompletion {
            if (shutdownJob === job) {
                shutdownJob = null
            }
        }
    }
}
