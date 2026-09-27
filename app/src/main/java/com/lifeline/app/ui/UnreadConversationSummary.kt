package com.lifeline.app.ui


internal enum class DirectMessageTransport {
    MESH
}

internal fun matchingUnreadAliases(
    unreadConversationIDs: Set<String>,
    canonicalConversationID: String,
    canonicalize: (String) -> String
): Set<String> {
    val normalizedCanonicalID = canonicalConversationID.lowercase()
    return unreadConversationIDs
        .filterTo(mutableSetOf()) { unreadID ->
            canonicalize(unreadID).equals(normalizedCanonicalID, ignoreCase = true)
        }
        .plus(canonicalConversationID)
        .mapTo(mutableSetOf()) { it.lowercase() }
}
