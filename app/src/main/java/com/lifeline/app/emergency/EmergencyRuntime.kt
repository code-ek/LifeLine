package com.lifeline.app.emergency

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.lifeline.app.mesh.MeshMessageTap
import com.lifeline.app.service.MeshServiceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Process-level entry point for emergency features. Talks to the LifeLine engine only through
 * [com.lifeline.app.mesh.MeshService] (send) and [MeshMessageTap] (receive), so it works
 * whether or not the chat UI is open and never takes the engine's single delegate slot.
 */
object EmergencyRuntime {
    private const val TAG = "EmergencyRuntime"

    /** Older SOS still appear in the feed (e.g. via gossip catch-up) but don't buzz the phone. */
    private const val NOTIFY_MAX_AGE_MS = 6 * 60 * 60 * 1000L

    private val store = EmergencyAlertStore()
    val alerts: StateFlow<List<SosAlert>> = store.alerts

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Holds only the application context, which lives as long as the process.
    @SuppressLint("StaticFieldLeak")
    private var notifier: EmergencyNotifier? = null

    @Synchronized
    fun initialize(context: Context) {
        if (notifier != null) return
        notifier = EmergencyNotifier(context.applicationContext)
        scope.launch {
            MeshMessageTap.messages.collect { message ->
                // Handle cancellations
                SosCodec.decodeCancel(message.content)?.let { cancelledId ->
                    store.remove(cancelledId)
                    Log.i(TAG, "SOS $cancelledId cancelled")
                    return@collect
                }
                val now = System.currentTimeMillis()
                val alert = store.ingest(message, currentPeerID(), now) ?: return@collect
                Log.i(TAG, "SOS ${alert.payload.id} (${alert.payload.type}) received")
                if (!alert.isLocal && now - alert.payload.timestampMs < NOTIFY_MAX_AGE_MS) {
                    notifier?.showIncoming(alert)
                }
            }
        }
    }

    /** Broadcasts an SOS over the mesh. Returns false if the mesh service is unavailable. */
    fun sendSos(
        context: Context,
        type: EmergencyType,
        description: String,
        location: SosLocation?
    ): Boolean {
        initialize(context)
        val mesh = try {
            MeshServiceHolder.getUnifiedOrCreate(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Mesh service unavailable: ${e.message}")
            return false
        }
        val payload = SosPayload(
            id = SosCodec.newId(),
            type = type,
            description = SosCodec.sanitizeDescription(description),
            timestampMs = System.currentTimeMillis(),
            location = location
        )
        mesh.sendMessage(SosCodec.encode(payload))
        store.addLocal(payload, myNickname = null, myPeerID = mesh.myPeerID, nowMs = payload.timestampMs)
        Log.i(TAG, "SOS ${payload.id} (${payload.type}) broadcast")
        return true
    }

    fun cancelSos(context: Context, id: String): Boolean {
        val mesh = try {
            MeshServiceHolder.getUnifiedOrCreate(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Mesh service unavailable for cancel: ${e.message}")
            return false
        }
        mesh.sendMessage(SosCodec.encodeCancel(id))
        store.remove(id)
        Log.i(TAG, "SOS $id cancelled and broadcast")
        return true
    }

    fun nearbyPeerCount(): Int = try {
        MeshServiceHolder.unifiedMeshService?.getActivePeerCount() ?: 0
    } catch (_: Exception) { 0 }

    private fun currentPeerID(): String? = try {
        MeshServiceHolder.unifiedMeshService?.myPeerID
    } catch (_: Exception) { null }
}
