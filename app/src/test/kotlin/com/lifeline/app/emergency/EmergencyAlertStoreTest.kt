package com.lifeline.app.emergency

import com.lifeline.app.model.LifeLineMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class EmergencyAlertStoreTest {

    private val myPeerID = "aaaaaaaaaaaaaaaa"

    private fun payload(id: String = "0123456789abcdef", ts: Long = 1_000L) = SosPayload(
        id = id,
        type = EmergencyType.FIRE,
        description = "Smoke in stairwell",
        timestampMs = ts,
        location = null
    )

    private fun meshMessage(
        content: String,
        senderPeerID: String = "bbbbbbbbbbbbbbbb",
        isPrivate: Boolean = false
    ) = LifeLineMessage(
        sender = "responder",
        content = content,
        timestamp = Date(0),
        senderPeerID = senderPeerID,
        isPrivate = isPrivate
    )

    @Test
    fun ingestsBroadcastSos() {
        val store = EmergencyAlertStore()
        val alert = store.ingest(meshMessage(SosCodec.encode(payload())), myPeerID, nowMs = 5_000L)

        assertEquals("0123456789abcdef", alert?.payload?.id)
        assertEquals("responder", alert?.senderNickname)
        assertEquals("bbbbbbbbbbbbbbbb", alert?.senderPeerID)
        assertEquals(5_000L, alert?.receivedAtMs)
        assertFalse(alert!!.isLocal)
        assertEquals(listOf(alert), store.alerts.value)
    }

    @Test
    fun ignoresOrdinaryChatAndPrivateMessages() {
        val store = EmergencyAlertStore()
        assertNull(store.ingest(meshMessage("hi"), myPeerID, 0L))
        assertNull(store.ingest(meshMessage(SosCodec.encode(payload()), isPrivate = true), myPeerID, 0L))
        assertTrue(store.alerts.value.isEmpty())
    }

    @Test
    fun deduplicatesBySosIdAcrossTransportsAndSync() {
        val store = EmergencyAlertStore()
        val content = SosCodec.encode(payload())
        assertTrue(store.ingest(meshMessage(content), myPeerID, 1L) != null)
        assertNull(store.ingest(meshMessage(content), myPeerID, 2L))
        assertEquals(1, store.alerts.value.size)
    }

    @Test
    fun ownSosEchoedBackIsNotReportedAsNew() {
        val store = EmergencyAlertStore()
        store.addLocal(payload(), myNickname = "me", myPeerID = myPeerID, nowMs = 1L)
        assertNull(store.ingest(meshMessage(SosCodec.encode(payload()), senderPeerID = myPeerID), myPeerID, 2L))
        assertTrue(store.alerts.value.single().isLocal)
    }

    @Test
    fun newestSosFirstAndCapped() {
        val store = EmergencyAlertStore(maxAlerts = 2)
        store.ingest(meshMessage(SosCodec.encode(payload(id = "1111111111111111", ts = 1L))), myPeerID, 0L)
        store.ingest(meshMessage(SosCodec.encode(payload(id = "3333333333333333", ts = 3L))), myPeerID, 0L)
        store.ingest(meshMessage(SosCodec.encode(payload(id = "2222222222222222", ts = 2L))), myPeerID, 0L)

        assertEquals(listOf("3333333333333333", "2222222222222222"), store.alerts.value.map { it.payload.id })
    }
}
