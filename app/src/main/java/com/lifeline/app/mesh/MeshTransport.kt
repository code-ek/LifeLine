package com.lifeline.app.mesh

import com.lifeline.app.model.RoutedPacket
import com.lifeline.app.protocol.LifeLinePacket

/**
 * Transport abstraction used by MeshCore to send packets via a specific medium.
 */
interface MeshTransport {
    val id: String

    /**
     * Broadcasts a packet and reports whether at least one concrete transport write was accepted.
     */
    fun broadcastPacket(routed: RoutedPacket): Boolean

    fun sendPacketToPeer(peerID: String, packet: LifeLinePacket): Boolean

    /**
     * Send through an exact transport generation rather than a reusable peer alias.
     * Transports that cannot prove the link identity must decline the operation.
     */
    fun sendPacketToLink(
        relayAddress: String,
        ingressLinkID: String,
        packet: LifeLinePacket
    ): Boolean = false

    fun cancelTransfer(transferId: String): Boolean = false

    fun getDeviceAddressForPeer(peerID: String): String? = null

    fun getDeviceAddressToPeerMapping(): Map<String, String> = emptyMap()

    fun getTransportDebugInfo(): String = ""
}
