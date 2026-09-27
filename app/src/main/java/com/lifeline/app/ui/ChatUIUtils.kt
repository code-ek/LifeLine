package com.lifeline.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lifeline.app.model.LifeLineMessage
import com.lifeline.app.ui.theme.BASE_FONT_SIZE
import com.lifeline.app.ui.theme.LifeLinePalette
import com.lifeline.app.ui.theme.ChatVisualTokens
import com.lifeline.app.ui.theme.colorForPeer
import java.text.SimpleDateFormat
import java.util.*

/**
 * Text formatting helpers for chat messages
 */

/** Opacity applied to the `#abcd` disambiguation suffix so the readable name dominates. */
internal const val SUFFIX_ALPHA = ChatVisualTokens.SenderSuffixAlpha

/** Compact transcript timestamp; seconds add noise without helping conversation scanning. */
internal const val CHAT_TIMESTAMP_PATTERN = "HH:mm"

/** Background opacity for a mention chip referring to somebody else. */
internal const val MENTION_CHIP_ALPHA = ChatVisualTokens.HighlightAlpha

/**
 * Mention token grammar shared by rendered messages and the composer.
 *
 * The optional `#abcd` suffix is part of the mention because it disambiguates peers that use the
 * same nickname. Keeping one regex prevents the composer from styling only `@name` while the
 * rendered transcript styles the complete token.
 */
internal val MENTION_TOKEN_REGEX = Regex("@([\\p{L}0-9_]+(?:#[a-fA-F0-9]{4})?)")

/** Thin space (U+2009) separating a display name from its `#abcd` disambiguation suffix. */
internal const val SUFFIX_THIN_SPACE = " "

/**
 * Build the sender label shown above the first message of a group.
 *
 * Renders `@name` plus a dimmed `#abcd` suffix, separated by a thin space. The name carries
 * a `nickname_click` annotation for everyone except yourself.
 */
fun formatTextMessageSender(
    message: LifeLineMessage,
    currentUserNickname: String,
    myPeerID: String,
    palette: LifeLinePalette
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val isSelf = message.isFromSelf(currentUserNickname, myPeerID)
    val senderColor = if (isSelf) {
        palette.accentOrange
    } else {
        colorForPeer(peerIdentityForMessage(message), palette)
    }
    val senderWeight = FontWeight.SemiBold
    val (baseName, suffix) = splitSuffix(message.sender)

    builder.pushStyle(
        SpanStyle(
            color = senderColor,
            fontSize = ChatVisualTokens.SenderFontSize,
            fontWeight = senderWeight
        )
    )
    builder.append("@")
    val nicknameStart = builder.length
    builder.append(truncateNickname(baseName))
    val nicknameEnd = builder.length
    if (!isSelf) {
        builder.addStringAnnotation(
            tag = "nickname_click",
            annotation = message.originalSender ?: message.sender,
            start = nicknameStart,
            end = nicknameEnd
        )
    }
    builder.pop()

    if (suffix.isNotEmpty()) {
        builder.append(SUFFIX_THIN_SPACE)
        builder.pushStyle(
            SpanStyle(
                color = senderColor.copy(alpha = SUFFIX_ALPHA),
                fontSize = ChatVisualTokens.SenderFontSize,
                fontWeight = FontWeight.Normal
            )
        )
        builder.append(suffix)
        builder.pop()
    }

    return builder.toAnnotatedString()
}

/**
 * Build the compact timestamp and optional proof-of-work label.
 *
 * Used standalone by media rows; text messages get the same span appended inline to the end of
 * their body via [appendBodyTimestamp].
 */
fun formatTextMessageMetadata(
    message: LifeLineMessage,
    timeFormatter: SimpleDateFormat = SimpleDateFormat(CHAT_TIMESTAMP_PATTERN, Locale.getDefault())
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    builder.pushStyle(
        SpanStyle(
            color = Color.Gray.copy(alpha = 0.7f),
            fontSize = (BASE_FONT_SIZE - 4).sp
        )
    )
    builder.append(timeFormatter.format(message.timestamp))
    message.powDifficulty?.takeIf { it > 0 }?.let { bits ->
        builder.append(" ⛨${bits}b")
    }
    builder.pop()
    return builder.toAnnotatedString()
}

/**
 * Append the timestamp (and optional PoW difficulty) directly after the message body so it
 * trails the final words rather than occupying its own column.
 *
 * Deliberately carries no click annotation: the timestamp is decoration, and making it
 * tappable would create dead zones inside the message body.
 */
private fun appendTimestampText(
    builder: AnnotatedString.Builder,
    message: LifeLineMessage,
    timeFormatter: SimpleDateFormat
) {
    builder.append("  ")
    builder.append(timeFormatter.format(message.timestamp))
    message.powDifficulty?.takeIf { it > 0 }?.let { bits ->
        builder.append(" ⛨${bits}b")
    }
}

private fun appendBodyTimestamp(
    builder: AnnotatedString.Builder,
    message: LifeLineMessage,
    palette: LifeLinePalette,
    timeFormatter: SimpleDateFormat,
) {
    builder.pushStyle(
        SpanStyle(
            color = palette.textTertiary,
            fontSize = ChatVisualTokens.SystemTimeFontSize,
            fontWeight = FontWeight.Normal,
        )
    )
    appendTimestampText(builder, message, timeFormatter)
    builder.pop()
}

private fun appendMutedTimestamp(
    builder: AnnotatedString.Builder,
    message: LifeLineMessage,
    contentColor: Color,
    timeFormatter: SimpleDateFormat,
) {
    builder.pushStyle(
        SpanStyle(
            color = contentColor.copy(alpha = ChatVisualTokens.MutedTextAlpha),
            fontSize = ChatVisualTokens.SystemTimeFontSize,
            fontWeight = FontWeight.Normal,
        )
    )
    appendTimestampText(builder, message, timeFormatter)
    builder.pop()
}

/**
 * Build a system / background-action line, e.g. `// Connected to the mesh 11:09:56`.
 *
 * The `//` prefix reads as machine narration in a monospace context and is far quieter than
 * the previous `* italic asterisk *` treatment, which competed with real messages.
 */
fun formatSystemMessage(
    message: LifeLineMessage,
    contentColor: Color,
    timeFormatter: SimpleDateFormat = SimpleDateFormat(CHAT_TIMESTAMP_PATTERN, Locale.getDefault())
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    builder.pushStyle(
        SpanStyle(
            color = contentColor.copy(alpha = ChatVisualTokens.MutedTextAlpha),
            fontSize = ChatVisualTokens.SystemActionFontSize,
            fontWeight = FontWeight.Medium,
        )
    )
    builder.append("// ")
    builder.append(message.content)
    builder.pop()

    appendMutedTimestamp(builder, message, contentColor, timeFormatter)
    return builder.toAnnotatedString()
}

/**
 * Header line for media (image / audio / file) rows.
 *
 * Matches the text-message treatment: `@name#abcd` with no angle brackets, followed by an
 * inline trailing timestamp. Media rows have no body text to trail, so the timestamp sits on
 * the same line as the name.
 */
fun formatMessageHeaderAnnotatedString(
    message: LifeLineMessage,
    currentUserNickname: String,
    myPeerID: String,
    palette: LifeLinePalette,
    contentColor: Color,
    timeFormatter: SimpleDateFormat = SimpleDateFormat(CHAT_TIMESTAMP_PATTERN, Locale.getDefault()),
    includeSender: Boolean = true
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val isSelf = message.isFromSelf(currentUserNickname, myPeerID)

    if (message.sender == "system") {
        return formatSystemMessage(message, contentColor, timeFormatter)
    }

    if (includeSender) {
        val baseColor = if (isSelf) {
            palette.accentOrange
        } else {
            colorForPeer(peerIdentityForMessage(message), palette)
        }
        val (baseName, suffix) = splitSuffix(message.sender)

        builder.pushStyle(
            SpanStyle(
                color = baseColor,
                fontSize = ChatVisualTokens.SenderFontSize,
                fontWeight = FontWeight.SemiBold,
            )
        )
        builder.append("@")
        val nicknameStart = builder.length
        builder.append(truncateNickname(baseName))
        val nicknameEnd = builder.length
        if (!isSelf) {
            builder.addStringAnnotation(
                tag = "nickname_click",
                annotation = (message.originalSender ?: message.sender),
                start = nicknameStart,
                end = nicknameEnd
            )
        }
        builder.pop()

        if (suffix.isNotEmpty()) {
            builder.append(SUFFIX_THIN_SPACE)
            builder.pushStyle(
                SpanStyle(
                    color = baseColor.copy(alpha = SUFFIX_ALPHA),
                    fontSize = ChatVisualTokens.SenderFontSize,
                    fontWeight = FontWeight.Normal,
                )
            )
            builder.append(suffix)
            builder.pop()
        }
    }

    appendMutedTimestamp(builder, message, contentColor, timeFormatter)
    return builder.toAnnotatedString()
}

/**
 * Split a name into base and a '#abcd' suffix if present
 */
fun splitSuffix(name: String): Pair<String, String> {
    if (name.length < 5) return Pair(name, "")
    
    val suffix = name.takeLast(5)
    if (suffix.startsWith("#") && suffix.drop(1).all { 
        it.isDigit() || it.lowercaseChar() in 'a'..'f' 
    }) {
        val base = name.dropLast(5)
        return Pair(base, suffix)
    }
    
    return Pair(name, "")
}

internal fun resolveMentionPeerIdentity(
    mention: String,
    mentionPeerIdentities: Map<String, PeerIdentity>,
): PeerIdentity? {
    val mentionWithoutAt = mention.trim().removePrefix("@")
    val baseName = splitSuffix(mentionWithoutAt).first
    return mentionPeerIdentities[mentionWithoutAt.lowercase(Locale.ROOT)]
        ?: mentionPeerIdentities[baseName.lowercase(Locale.ROOT)]
}

/**
 * Resolve the deterministic color for a mention on every surface that displays one.
 *
 * Exact suffixed tokens win; an unsuffixed nickname is only present in the identity map when it is
 * unambiguous. The nickname fallback preserves the legacy behavior for peers with no stable ID.
 */
internal fun colorForMention(
    mention: String,
    mentionPeerIdentities: Map<String, PeerIdentity>,
    palette: LifeLinePalette,
): Color {
    val mentionWithoutAt = mention.trim().removePrefix("@")
    val identity = resolveMentionPeerIdentity(mentionWithoutAt, mentionPeerIdentities)
        ?: PeerIdentity.nickname(mentionWithoutAt)
    return colorForPeer(identity, palette)
}
