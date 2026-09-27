package com.lifeline.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lifeline.app.model.LifeLineMessage
import com.lifeline.app.ui.theme.LifeLineFontFamily
import com.lifeline.app.ui.theme.ChatVisualTokens
import com.lifeline.app.ui.theme.DarkLifeLineColorScheme
import com.lifeline.app.ui.theme.DarkLifeLinePalette
import com.lifeline.app.ui.theme.LightLifeLineColorScheme
import com.lifeline.app.ui.theme.LightLifeLinePalette
import com.lifeline.app.ui.theme.MessageSenderTextStyle
import com.lifeline.app.ui.theme.PeerColorStyle
import com.lifeline.app.ui.theme.colorForPeer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs under Robolectric because URL detection in message bodies goes through
 * `android.util.Patterns.WEB_URL`, which is null on a bare JVM.
 */
@RunWith(RobolectricTestRunner::class)
class ChatUIUtilsTest {
    private val timeFormatter = SimpleDateFormat(CHAT_TIMESTAMP_PATTERN, Locale.ROOT).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    private val palette = DarkLifeLinePalette
    private val colorScheme = DarkLifeLineColorScheme

    private fun message(
        content: String,
        sender: String = "alice",
        powDifficulty: Int? = null,
    ) = LifeLineMessage(
        sender = sender,
        content = content,
        timestamp = Date(0),
        powDifficulty = powDifficulty,
    )

    // Timestamp metadata

    @Test
    fun `text message metadata separates PoW badge with one space`() {
        assertEquals(
            "00:00 ⛨12b",
            formatTextMessageMetadata(message("hello", powDifficulty = 12), timeFormatter).text,
        )
    }

    @Test
    fun `text message metadata omits non-positive PoW difficulty`() {
        assertEquals(
            "00:00",
            formatTextMessageMetadata(message("hello", powDifficulty = 0), timeFormatter).text,
        )
    }

    // Body with inline trailing timestamp

    // Mention chips

    private fun mentionChipSpans(body: androidx.compose.ui.text.AnnotatedString) =
        body.spanStyles.filter { it.item.background.isSpecified() }

    private fun androidx.compose.ui.graphics.Color.isSpecified() =
        this != androidx.compose.ui.graphics.Color.Unspecified && alpha > 0f

    @Test
    fun `composer colors nickname and hash suffix from the mentioned peer identity`() {
        val pubkey = "0123456789abcdef".repeat(4)
        val identity = PeerIdentity.fromPublicKey(pubkey)
        val token = "@carol#04af"
        val input = "ping $token now"
        val transformed = MentionVisualTransformation(
            mentionPeerIdentities = mapOf("carol#04af" to identity),
            palette = palette,
        ).filter(AnnotatedString(input)).text

        val expectedColor = colorForPeer(identity, palette)
        val tokenStart = input.indexOf(token)
        val suffixStart = input.indexOf("#04af")
        val tokenEnd = tokenStart + token.length

        assertEquals(input, transformed.text)
        assertTrue(transformed.spanStyles.any {
            it.start == tokenStart &&
                it.end == tokenEnd &&
                it.item.background == expectedColor.copy(alpha = MENTION_CHIP_ALPHA)
        })
        assertTrue(transformed.spanStyles.any {
            it.start == tokenStart &&
                it.end == suffixStart &&
                it.item.color == expectedColor
        })
        assertTrue(transformed.spanStyles.any {
            it.start == suffixStart &&
                it.end == tokenEnd &&
                it.item.color == expectedColor.copy(alpha = SUFFIX_ALPHA)
        })
    }

    // System / action messages

    @Test
    fun `system message uses a double-slash prefix and no brackets`() {
        val text = formatSystemMessage(
            message = message("Connected to the mesh", sender = "system"),
            contentColor = colorScheme.onSurface,
            timeFormatter = timeFormatter,
        ).text

        assertEquals("// Connected to the mesh  00:00", text)
    }

    @Test
    fun `system message is not italic`() {
        // The old treatment was `* italic asterisks *`, which competed visually with real
        // messages despite being lower-priority narration.
        val annotated = formatSystemMessage(
            message = message("mesh restarting", sender = "system"),
            contentColor = colorScheme.onSurface,
            timeFormatter = timeFormatter,
        )

        assertTrue(annotated.spanStyles.all { it.item.fontStyle == null })
    }

    @Test
    fun `system action and timestamp use their expected weights sizes and opacity`() {
        val annotated = formatSystemMessage(
            message = message("mesh restarting", sender = "system"),
            contentColor = colorScheme.onSurface,
            timeFormatter = timeFormatter,
        )

        val action = annotated.spanStyles.first {
            annotated.text.substring(it.start, it.end) == "// mesh restarting"
        }.item
        val time = annotated.spanStyles.first {
            annotated.text.substring(it.start, it.end) == "  00:00"
        }.item

        assertEquals(12.sp, action.fontSize)
        assertEquals(FontWeight.Medium, action.fontWeight)
        assertEquals(colorScheme.onSurface.copy(alpha = 0.5f), action.color)
        assertEquals(10.sp, time.fontSize)
        assertEquals(FontWeight.Normal, time.fontWeight)
        assertEquals(colorScheme.onSurface.copy(alpha = 0.5f), time.color)
    }

    // Sender label

    @Test
    fun `sender label drops angle brackets and dims the hash suffix`() {
        val sender = formatTextMessageSender(
            message = message("hi", sender = "carol#04af"),
            currentUserNickname = "bob",
            myPeerID = "peer-me",
            palette = palette,
        )

        assertEquals("@carol #04af", sender.text)

        val suffixSpan = sender.spanStyles.first { sender.text.substring(it.start, it.end) == "#04af" }
        val nameSpan = sender.spanStyles.first { sender.text.substring(it.start, it.end) == "@carol" }
        assertNotNull(suffixSpan.item.color)
        assertEquals(14.sp, nameSpan.item.fontSize)
        assertEquals(FontWeight.SemiBold, nameSpan.item.fontWeight)
        assertEquals(14.sp, suffixSpan.item.fontSize)
        assertEquals(FontWeight.Normal, suffixSpan.item.fontWeight)
        assertEquals(ChatVisualTokens.SenderSuffixAlpha, suffixSpan.item.color.alpha)
        assertTrue(
            "suffix must be dimmer than the name",
            suffixSpan.item.color.alpha < nameSpan.item.color.alpha
        )
    }

    @Test
    fun `sender label annotates the nickname for others but not for yourself`() {
        val other = formatTextMessageSender(
            message = message("hi", sender = "carol#04af"),
            currentUserNickname = "bob",
            myPeerID = "peer-me",
            palette = palette,
        )
        assertEquals(1, other.getStringAnnotations("nickname_click", 0, other.length).size)

        val mine = formatTextMessageSender(
            message = message("hi", sender = "bob"),
            currentUserNickname = "bob",
            myPeerID = "peer-me",
            palette = palette,
        )
        assertTrue(mine.getStringAnnotations("nickname_click", 0, mine.length).isEmpty())
    }

    // Peer colors

    @Test
    fun `peer identity factories normalize stable IDs without resolving UI colors`() {
        assertEquals(
            PeerIdentity.mesh("abcdef"),
            PeerIdentity.mesh("ABCDEF")
        )
        assertEquals(
            PeerIdentity.fromPublicKey("abcdef"),
            PeerIdentity.fromPublicKey("ABCDEF")
        )
        assertEquals(
            PeerIdentity.fromPublicKey("abcdef"),
            PeerIdentity.fromPublicKey("key:key_ABCDEF")
        )
        assertEquals(
            "key:abcdef01",
            PeerIdentity.fromPublicKey("ABCDEF0123456789").stableKey
        )
        assertEquals("alice#1234", PeerIdentity.nickname("ALICE#1234").stableKey)
    }

    @Test
    fun `peer color hue is stable across light and dark, only chroma differs`() {
        // Hue derivation must stay identical on every phone; only saturation/value are tuned per
        // theme so dark mode stays muted-but-bright and light mode stays deep-but-readable.
        val identity = PeerIdentity.mesh("abc")
        val dark = colorForPeer(identity, DarkLifeLinePalette)
        val light = colorForPeer(identity, LightLifeLinePalette)

        val darkHsv = FloatArray(3)
        val lightHsv = FloatArray(3)
        rgbToHsv(dark.red, dark.green, dark.blue, darkHsv)
        rgbToHsv(light.red, light.green, light.blue, lightHsv)

        assertEquals(darkHsv[0].toDouble(), lightHsv[0].toDouble(), 1.0)
        assertEquals(PeerColorStyle.Dark.saturation.toDouble(), darkHsv[1].toDouble(), 0.01)
        assertEquals(PeerColorStyle.Dark.value.toDouble(), darkHsv[2].toDouble(), 0.01)
        assertEquals(PeerColorStyle.Light.saturation.toDouble(), lightHsv[1].toDouble(), 0.01)
        assertEquals(PeerColorStyle.Light.value.toDouble(), lightHsv[2].toDouble(), 0.01)
        // Dark theme: muted chroma, never dark (readable on near-black).
        assertTrue(darkHsv[1] < 0.75f)
        assertTrue(darkHsv[2] >= 0.75f)
        // Light theme: avoid neon / near-white peer labels.
        assertTrue(lightHsv[1] < 0.85f)
        assertTrue(lightHsv[2] <= 0.55f)
    }

    @Test
    fun `peer color avoids the orange hue reserved for self`() {
        // Sweep a range of seeds; none may land within the reserved orange band.
        repeat(500) { i ->
            val color = colorForPeer(
                PeerIdentity.mesh("seed$i"),
                DarkLifeLinePalette
            )
            val hsv = FloatArray(3)
            rgbToHsv(color.red, color.green, color.blue, hsv)
            val distanceFromOrange = kotlin.math.abs(hsv[0] - 30f)
            assertTrue(
                "seed$i resolved to ${hsv[0]}°, inside the reserved orange band",
                distanceFromOrange >= 17f || hsv[1] < 0.01f
            )
        }
    }

    @Test
    fun `material owns standard text while LifeLine palette owns peer chroma`() {
        assertEquals(Color(0xFFE8F0E8), DarkLifeLineColorScheme.onSurface)
        assertTrue(LightLifeLineColorScheme.onSurface != DarkLifeLineColorScheme.onSurface)
        assertTrue(
            LightLifeLinePalette.peerColors != DarkLifeLinePalette.peerColors
        )
    }

    @Test
    fun `mesh chat and people list resolve the same peer identity`() {
        val peerID = "ABCDEF0123456789"
        val peopleIdentity = PeerIdentity.mesh(peerID)
        val chatIdentity = peerIdentityForMessage(
            LifeLineMessage(
                sender = "alice#1234",
                content = "hello",
                timestamp = Date(0),
                senderPeerID = peerID,
            )
        )

        assertEquals(peopleIdentity, chatIdentity)
    }


    private fun rgbToHsv(r: Float, g: Float, b: Float, out: FloatArray) {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        out[0] = when {
            delta == 0f -> 0f
            max == r -> (60f * (((g - b) / delta) % 6f) + 360f) % 360f
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        out[1] = if (max == 0f) 0f else delta / max
        out[2] = max
    }
}
