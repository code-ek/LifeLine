package com.lifeline.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeline.app.R

/**
 * The bundled Nunito variable font used throughout the app.
 *
 * Keeping the font in the APK preserves offline behavior and keeps text metrics identical on
 * every device.
 */
@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: FontWeight) = Font(
    R.font.nunito_variable,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

/** LifeLine's typeface: Nunito, a friendly rounded sans (one bundled variable font, works offline). */
internal val LifeLineFontFamily = FontFamily(
    nunito(FontWeight.Normal),
    nunito(FontWeight.Medium),
    nunito(FontWeight.SemiBold),
    nunito(FontWeight.Bold),
    nunito(FontWeight.ExtraBold),
)

/** Exact typography, spacing, and opacity values exported for the chat transcript. */
internal object ChatVisualTokens {
    val MessageBodyFontSize: TextUnit = 14.sp
    val MessageBodyLineHeight: TextUnit = 20.sp
    val SenderFontSize: TextUnit = 14.sp
    val SenderLineHeight: TextUnit = 16.sp
    val SystemActionFontSize: TextUnit = 12.sp
    val SystemActionLineHeight: TextUnit = 16.sp
    val SystemTimeFontSize: TextUnit = 10.sp

    val MessageItemSpacing: Dp = 8.dp
    val SenderTopPadding: Dp = 8.dp
    val SenderToBodySpacing: Dp = 4.dp

    // Bubble geometry

    /** Rounded corner on the three "free" corners of a message bubble. */
    val BubbleCornerRadius: Dp = 16.dp

    /** Tightened corner on the speaker's own side, giving the bubble a subtle tail. */
    val BubbleTailRadius: Dp = 4.dp

    /** Padding inside a bubble, around the text. */
    val BubblePaddingHorizontal: Dp = 12.dp
    val BubblePaddingVertical: Dp = 8.dp

    /** A bubble never grows past this fraction of the list width, so long lines still wrap. */
    const val BubbleMaxWidthFraction: Float = 0.80f

    /**
     * Author-colour wash inside a bubble. Matches the mention-chip treatment so a tinted
     * bubble stays legible on both the near-black and near-white chat surfaces.
     */
    const val BubbleBackgroundAlpha: Float = 0.18f

    /** Author-colour hairline around a bubble; stronger than the fill so the shape reads. */
    const val BubbleBorderAlpha: Float = 0.38f

    const val SenderSuffixAlpha: Float = 0.60f
    const val HighlightAlpha: Float = 0.20f
    const val MutedTextAlpha: Float = 0.50f

    val MessageBodyStyle = TextStyle(
        fontFamily = LifeLineFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = MessageBodyFontSize,
        lineHeight = MessageBodyLineHeight,
    )

    val SenderStyle = TextStyle(
        fontFamily = LifeLineFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = SenderFontSize,
        lineHeight = SenderLineHeight,
    )

    val SystemActionStyle = TextStyle(
        fontFamily = LifeLineFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = SystemActionFontSize,
        lineHeight = SystemActionLineHeight,
    )
}
