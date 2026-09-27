package com.lifeline.app.mesh

import com.lifeline.app.model.LifeLineMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class MeshMessageTapTest {

    @Test
    fun publishedMessagesReachObservers() = runTest(UnconfinedTestDispatcher()) {
        val message = LifeLineMessage(sender = "peer", content = "hello", timestamp = Date(0))
        val received = async { MeshMessageTap.messages.first { it.id == message.id } }

        MeshMessageTap.publish(message)

        assertEquals(message, received.await())
    }

    @Test
    fun publishWithoutObserversDoesNotBlockOrThrow() {
        repeat(200) {
            MeshMessageTap.publish(LifeLineMessage(sender = "peer", content = "m$it", timestamp = Date(0)))
        }
    }
}
