package com.lifeline.app.services

import android.content.Context
import com.lifeline.app.identity.SecureIdentityStateManager
import com.lifeline.app.mesh.MeshService
import com.lifeline.app.model.LifeLineMessage

object ContactDirectory {
    data class ContactResolution(
        val conversationID: String,
        val meshPeerID: String?,
        val noisePublicKey: ByteArray?,
        val displayName: String?
    ) {
        val noiseKeyHex: String? get() = noisePublicKey?.let { ContactIdentityResolver.noiseKeyHex(it) }
    }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var meshProvider: (() -> MeshService?)? = null

    @Volatile
    internal var identityManagerProvider: (Context) -> SecureIdentityStateManager =
        { SecureIdentityStateManager(it) }

    fun initialize(context: Context, meshProvider: () -> MeshService?) {
        appContext = context.applicationContext
        this.meshProvider = meshProvider
    }

    fun isContactConversationID(value: String): Boolean =
        ContactIdentityResolver.isContactConversationId(value)

    fun canonicalConversationId(peerOrConversationID: String): String {
        val value = peerOrConversationID.trim()
        if (ContactIdentityResolver.isContactConversationId(value)) return value.lowercase()

        noiseKeyForAlias(value)?.let {
            return ContactIdentityResolver.contactConversationIdForNoiseKey(it)
        }

        return value
    }

    fun resolve(peerOrConversationID: String): ContactResolution {
        val conversationID = canonicalConversationId(peerOrConversationID)
        val contactFingerprint = ContactIdentityResolver.fingerprintFromContactConversationId(conversationID)

        val noiseKey = peerOrConversationID
            .takeIf { ContactIdentityResolver.isNoiseKeyHex(it) }
            ?.let { ContactIdentityResolver.bytesFromHex(it) }
        val liveMeshPeerID = contactFingerprint?.let { findLiveMeshPeerForFingerprint(it) }
            ?: peerOrConversationID.takeIf { ContactIdentityResolver.isMeshPeerId(it) && isMeshPeerConnected(it) }

        return ContactResolution(
            conversationID = conversationID,
            meshPeerID = liveMeshPeerID,
            noisePublicKey = noiseKey ?: liveMeshPeerID?.let { meshProvider?.invoke()?.getPeerInfo(it)?.noisePublicKey },
            // A connected peer's current announcement is authoritative. The cached fingerprint
            // nickname is an offline fallback and can legitimately be older.
            displayName = liveMeshPeerID?.let { meshProvider?.invoke()?.getPeerInfo(it)?.nickname }
                ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
                ?: contactFingerprint?.let { cachedFingerprintNickname(it) }
        )
    }

    fun canonicalizePrivateChats(
        chats: Map<String, List<LifeLineMessage>>
    ): Map<String, List<LifeLineMessage>> {
        if (chats.isEmpty()) return chats

        val merged = linkedMapOf<String, MutableList<LifeLineMessage>>()
        chats.forEach { (key, messages) ->
            val canonical = canonicalConversationId(key)
            val list = merged.getOrPut(canonical) { mutableListOf() }
            list.addAll(messages)
        }

        return merged.mapValues { (_, messages) ->
            // A private message's timestamp comes from the sender and is not a reliable ordering
            // signal when peers' clocks differ. Use the local receipt sequence so interleaved
            // alias lists can be merged back into their global arrival order.
            PrivateMessageArrivalOrder.order(messages.distinctBy { it.id })
        }
    }

    fun aliasesForConversation(peerOrConversationID: String): Set<String> {
        val resolution = resolve(peerOrConversationID)
        val aliases = mutableSetOf<String>()
        aliases.add(peerOrConversationID)
        aliases.add(resolution.conversationID)
        resolution.meshPeerID?.let { aliases.add(it) }
        resolution.noiseKeyHex?.let { aliases.add(it) }
        return aliases
    }

    private fun noiseKeyForAlias(value: String): ByteArray? {
        if (ContactIdentityResolver.isNoiseKeyHex(value)) {
            return ContactIdentityResolver.bytesFromHex(value)
        }

        if (ContactIdentityResolver.isMeshPeerId(value)) {
            meshProvider?.invoke()?.getPeerInfo(value)?.noisePublicKey?.let { return it }
            cachedNoiseKey(value)?.let { return it }
        }

        return null
    }

    private fun cachedNoiseKey(peerID: String): ByteArray? {
        val context = appContext ?: return null
        return try {
            identityManagerProvider(context)
                .getCachedNoiseKey(peerID)
                ?.let { ContactIdentityResolver.bytesFromHex(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun cachedFingerprintNickname(fingerprint: String): String? {
        val context = appContext ?: return null
        return try {
            identityManagerProvider(context)
                .getCachedFingerprintNickname(fingerprint)
                ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
        } catch (_: Exception) {
            null
        }
    }

    private fun findLiveMeshPeerForFingerprint(fingerprint: String): String? {
        val mesh = meshProvider?.invoke() ?: return null
        return mesh.getPeerNicknames().keys.firstOrNull { peerID ->
            val info = mesh.getPeerInfo(peerID)
            val noiseKey = info?.noisePublicKey ?: cachedNoiseKey(peerID)
            noiseKey != null &&
                ContactIdentityResolver.fingerprintHex(noiseKey).equals(fingerprint, ignoreCase = true) &&
                info?.isConnected == true
        }
    }

    private fun isMeshPeerConnected(peerID: String): Boolean =
        try {
            meshProvider?.invoke()?.getPeerInfo(peerID)?.isConnected == true
        } catch (_: Exception) {
            false
        }
}
