package com.lifeline.app.emergency

import java.security.SecureRandom
import java.util.Locale

/**
 * Encodes an SOS as the text body of an ordinary public mesh MESSAGE.
 *
 * Riding on MESSAGE (rather than a new packet type) gives SOS the engine's signing, multi-hop
 * relay, TTL, dedup and gossip-sync catch-up for free, and older clients simply show the
 * human-readable first line. See docs/emergency/ENGINE_INTERFACE_MAP.md.
 *
 * Wire format (two lines):
 * ```
 * 🆘 SOS [MEDICAL] Person unconscious, breathing
 * sos/1 id=0123456789abcdef t=MEDICAL ts=1700000000000 loc=1.23456,2.34567 acc=12
 * ```
 * Decoding is fail-closed: anything malformed is treated as normal chat, not an SOS.
 */
object SosCodec {
    const val MAX_DESCRIPTION_CHARS = 280

    private const val HEADER_PREFIX = "🆘 SOS ["
    private const val META_PREFIX = "sos/1 "
    private const val CANCEL_HEADER = "🆘 SOS CANCEL"
    private const val CANCEL_META_PREFIX = "sos/1 cancel "
    private val ID_PATTERN = Regex("[0-9a-f]{8,32}")
    private val random = SecureRandom()

    fun newId(): String {
        val bytes = ByteArray(8).also(random::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun encodeCancel(id: String, timestampMs: Long = System.currentTimeMillis()): String =
        "$CANCEL_HEADER\n${CANCEL_META_PREFIX}id=$id ts=$timestampMs"

    fun decodeCancel(content: String): String? {
        val lines = content.lines()
        if (lines.size < 2) return null
        if (lines[0] != CANCEL_HEADER || !lines[1].startsWith(CANCEL_META_PREFIX)) return null
        val fields = lines[1].removePrefix(CANCEL_META_PREFIX)
            .split(' ')
            .mapNotNull { token ->
                val eq = token.indexOf('=')
                if (eq <= 0) null else token.substring(0, eq) to token.substring(eq + 1)
            }
            .toMap()
        return fields["id"]?.takeIf { ID_PATTERN.matches(it) }
    }

    fun sanitizeDescription(raw: String): String =
        raw.replace(Regex("[\\r\\n\\t]+"), " ")
            .filter { !it.isISOControl() }
            .replace(Regex(" {2,}"), " ")
            .trim()
            .take(MAX_DESCRIPTION_CHARS)
            .trimEnd()

    fun encode(payload: SosPayload): String {
        val description = sanitizeDescription(payload.description)
        val header = buildString {
            append(HEADER_PREFIX).append(payload.type.name).append(']')
            if (description.isNotEmpty()) append(' ').append(description)
        }
        val meta = buildString {
            append(META_PREFIX)
            append("id=").append(payload.id)
            append(" t=").append(payload.type.name)
            append(" ts=").append(payload.timestampMs)
            payload.location?.let { loc ->
                append(" loc=")
                append(String.format(Locale.US, "%.5f,%.5f", loc.latitude, loc.longitude))
                loc.accuracyMeters?.let { append(" acc=").append(it.toInt()) }
            }
        }
        return "$header\n$meta"
    }

    fun decode(content: String): SosPayload? {
        val lines = content.lines()
        if (lines.size < 2) return null
        val header = lines[0]
        val metaLine = lines[1]
        if (!header.startsWith(HEADER_PREFIX) || !metaLine.startsWith(META_PREFIX)) return null

        val closing = header.indexOf(']', HEADER_PREFIX.length)
        if (closing < 0) return null
        val description = sanitizeDescription(header.substring(closing + 1))

        val fields = metaLine.removePrefix(META_PREFIX)
            .split(' ')
            .mapNotNull { token ->
                val eq = token.indexOf('=')
                if (eq <= 0) null else token.substring(0, eq) to token.substring(eq + 1)
            }
            .toMap()

        val id = fields["id"]?.takeIf { ID_PATTERN.matches(it) } ?: return null
        val type = EmergencyType.fromWire(fields["t"] ?: return null)
        val timestampMs = fields["ts"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val location = fields["loc"]?.let { parseLocation(it, fields["acc"]) ?: return null }

        return SosPayload(id, type, description, timestampMs, location)
    }

    private fun parseLocation(value: String, accuracy: String?): SosLocation? {
        val parts = value.split(',')
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        if (!lat.isFinite() || !lon.isFinite()) return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val acc = accuracy?.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }
        return SosLocation(lat, lon, acc)
    }
}
