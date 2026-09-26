package com.lifeline.app.mesh

import com.lifeline.app.model.LifeLineMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide, read-only stream of every admitted incoming mesh message.
 *
 * Unlike [MeshDelegate], which has a single slot owned by the chat UI and is nulled while the UI
 * is closed, this tap emits regardless of UI state and supports any number of observers. It is
 * purely additive: publishing never blocks and never changes engine behaviour.
 */
object MeshMessageTap {
    private val _messages = MutableSharedFlow<LifeLineMessage>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<LifeLineMessage> = _messages.asSharedFlow()

    fun publish(message: LifeLineMessage) {
        _messages.tryEmit(message)
    }
}
