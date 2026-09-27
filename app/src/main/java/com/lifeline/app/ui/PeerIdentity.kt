package com.lifeline.app.ui

import com.lifeline.app.model.LifeLineMessage
import java.util.Locale

/**
 * Canonical, presentation-neutral identity used to derive a peer hue.
 *
 * Callers cannot construct arbitrary color seeds. Every identity is normalized and namespaced
 * here so the same user resolves to the same color on every surface.
 */
@JvmInline
value class PeerIdentity private constructor(internal val stableKey: String) {
    companion object {
        fun mesh(peerID: String): PeerIdentity =
            PeerIdentity("noise:${normalize(peerID)}")

        /** Identity derived from a public key hex string. */
        fun fromPublicKey(pubkeyHex: String): PeerIdentity {
            val normalized = normalizeKeyIdentifier(pubkeyHex)
            return PeerIdentity("key:${normalized.take(8)}")
        }

        /** Last-resort identity for legacy messages and mentions that carry no stable peer ID. */
        fun nickname(nickname: String): PeerIdentity =
            PeerIdentity(normalize(nickname))

        private fun normalize(value: String): String =
            value.trim().lowercase(Locale.ROOT)

        private fun normalizeKeyIdentifier(value: String): String {
            var normalized = normalize(value)
            // Strip any routing prefixes
            for (prefix in listOf("key:", "key_")) {
                while (normalized.startsWith(prefix)) {
                    normalized = normalized.removePrefix(prefix)
                }
            }
            return normalized
        }
    }
}

/**
 * Resolve the canonical identity attached to a rendered message.
 */
fun peerIdentityForMessage(message: LifeLineMessage): PeerIdentity {
    val senderPeerID = message.senderPeerID
    return when {
        senderPeerID?.length == 16 || senderPeerID?.length == 64 -> {
            PeerIdentity.mesh(senderPeerID)
        }
        else -> PeerIdentity.nickname(message.sender)
    }
}
