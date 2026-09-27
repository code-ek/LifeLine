package com.lifeline.app.ui

import com.lifeline.app.model.LifeLineMessage
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageInteractionUtilsTest {
    @Test
    fun `self detection accepts peer id nickname and nickname suffix`() {
        assertTrue(message(sender = "alice", senderPeerId = "peer-a").isFromSelf("me", "peer-a"))
        assertTrue(message(sender = "me").isFromSelf("me", "peer-a"))
        assertTrue(message(sender = "me#1a2b").isFromSelf("me", "peer-a"))
    }

    @Test
    fun `self detection rejects unrelated sender`() {
        assertFalse(message(sender = "alice", senderPeerId = "peer-b").isFromSelf("me", "peer-a"))
    }

    private fun message(sender: String, senderPeerId: String? = null): LifeLineMessage =
        LifeLineMessage(
            sender = sender,
            content = "hello",
            timestamp = Date(0),
            senderPeerID = senderPeerId,
        )
}
