package com.lifeline.app.services

import com.lifeline.app.util.dataFromHexString
import com.lifeline.app.util.hexEncodedString
import java.security.MessageDigest

object ContactIdentityResolver {
    private const val CONTACT_PREFIX = "contact_"
    private val meshPeerIdRegex = Regex("^[0-9a-fA-F]{16}$")
    private val noiseKeyRegex = Regex("^[0-9a-fA-F]{64}$")
    private val fingerprintRegex = Regex("^[0-9a-fA-F]{64}$")

    fun isMeshPeerId(value: String): Boolean = meshPeerIdRegex.matches(value)

    fun isNoiseKeyHex(value: String): Boolean = noiseKeyRegex.matches(value)

    fun noiseKeyHex(noisePublicKey: ByteArray): String = noisePublicKey.hexEncodedString()

    fun fingerprintHex(noisePublicKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(noisePublicKey)
        return digest.hexEncodedString()
    }

    fun contactConversationIdForNoiseKey(noisePublicKey: ByteArray): String =
        CONTACT_PREFIX + fingerprintHex(noisePublicKey)

    fun contactConversationIdForFingerprint(fingerprint: String): String? =
        fingerprint
            .takeIf { fingerprintRegex.matches(it) }
            ?.let { CONTACT_PREFIX + it.lowercase() }

    fun isContactConversationId(value: String): Boolean =
        value.startsWith(CONTACT_PREFIX) &&
            fingerprintRegex.matches(value.removePrefix(CONTACT_PREFIX))

    fun fingerprintFromContactConversationId(value: String): String? =
        value
            .takeIf { isContactConversationId(it) }
            ?.removePrefix(CONTACT_PREFIX)
            ?.lowercase()

    fun peerIdForNoiseKey(noisePublicKey: ByteArray): String =
        fingerprintHex(noisePublicKey).take(16)

    fun peerIdForNoiseKeyHex(noiseKeyHex: String): String? =
        bytesFromHex(noiseKeyHex)
            ?.takeIf { it.size == 32 }
            ?.let { peerIdForNoiseKey(it) }

    fun bytesFromHex(hex: String): ByteArray? {
        val clean = hex.trim()
        if (clean.length % 2 != 0) return null
        if (!clean.matches(Regex("^[0-9a-fA-F]+$"))) return null
        return clean.dataFromHexString()
    }
}
