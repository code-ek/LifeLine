package com.lifeline.app.emergency

import com.lifeline.app.model.LifeLineMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory SOS feed, newest first. Deduplicates by SOS id because the same alert can arrive
 * over several transports and again through gossip sync.
 */
class EmergencyAlertStore(private val maxAlerts: Int = 200) {

    private val _alerts = MutableStateFlow<List<SosAlert>>(emptyList())
    val alerts: StateFlow<List<SosAlert>> = _alerts.asStateFlow()

    // Kept separately from the capped list so an evicted alert is not re-added by a late sync.
    private val seenIds = LinkedHashSet<String>()

    /** Returns the new alert, or null if [message] is not a new broadcast SOS. */
    @Synchronized
    fun ingest(message: LifeLineMessage, myPeerID: String?, nowMs: Long): SosAlert? {
        if (message.isPrivate) return null
        val payload = SosCodec.decode(message.content) ?: return null
        if (!markSeen(payload.id)) return null
        val alert = SosAlert(
            payload = payload,
            senderPeerID = message.senderPeerID,
            senderNickname = message.sender,
            receivedAtMs = nowMs,
            isLocal = myPeerID != null && message.senderPeerID == myPeerID
        )
        insert(alert)
        return alert
    }

    @Synchronized
    fun addLocal(payload: SosPayload, myNickname: String?, myPeerID: String?, nowMs: Long): SosAlert {
        markSeen(payload.id)
        val alert = SosAlert(payload, myPeerID, myNickname, nowMs, isLocal = true)
        insert(alert)
        return alert
    }

    private fun markSeen(id: String): Boolean {
        if (!seenIds.add(id)) return false
        if (seenIds.size > maxAlerts * 5) seenIds.remove(seenIds.first())
        return true
    }

    @Synchronized
    fun remove(id: String) {
        _alerts.update { current -> current.filter { it.payload.id != id } }
    }

    private fun insert(alert: SosAlert) {
        _alerts.update { current ->
            (current + alert)
                .sortedByDescending { it.payload.timestampMs }
                .take(maxAlerts)
        }
    }
}
