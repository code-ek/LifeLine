package com.lifeline.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Base font size for consistent scaling across the app
internal const val BASE_FONT_SIZE = com.lifeline.app.util.AppConstants.UI.BASE_FONT_SIZE_SP

/** Sender label above a message group. Single line, never wraps. */
val MessageSenderTextStyle = ChatVisualTokens.SenderStyle

private fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = LifeLineFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp
)

/**
 * LifeLine type scale: Nunito throughout, sized up for easy reading (body text 16–17 sp,
 * bold headings) so the app stays usable under stress and for people with weaker eyesight.
 */
val Typography = Typography(
    displayLarge = style(48, 56, FontWeight.ExtraBold),
    displayMedium = style(40, 48, FontWeight.ExtraBold),
    displaySmall = style(36, 44, FontWeight.ExtraBold),
    headlineLarge = style(32, 40, FontWeight.ExtraBold),
    headlineMedium = style(28, 36, FontWeight.ExtraBold),
    headlineSmall = style(24, 32, FontWeight.Bold),
    titleLarge = style(22, 28, FontWeight.ExtraBold),
    titleMedium = style(18, 24, FontWeight.Bold),
    titleSmall = style(16, 22, FontWeight.Bold),
    bodyLarge = style(17, 24, FontWeight.Normal),
    bodyMedium = style(16, 22, FontWeight.Normal),
    bodySmall = style(14, 20, FontWeight.Normal),
    labelLarge = style(16, 20, FontWeight.Bold),
    labelMedium = style(14, 18, FontWeight.SemiBold),
    labelSmall = style(12, 16, FontWeight.SemiBold),
)
