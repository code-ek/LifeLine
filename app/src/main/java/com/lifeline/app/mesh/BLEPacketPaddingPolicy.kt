package com.lifeline.app.mesh

import com.lifeline.app.protocol.MessageType

/**
 * wire-compatible BLE padding policy.
 *
 * Padding decision per packet type:
 * only Noise frames are padded over BLE.
 */
object BLEPacketPaddingPolicy {
    fun shouldPadForBLE(type: UByte): Boolean {
        return when (MessageType.fromValue(type)) {
            MessageType.NOISE_ENCRYPTED, MessageType.NOISE_HANDSHAKE -> true
            else -> false
        }
    }
}
