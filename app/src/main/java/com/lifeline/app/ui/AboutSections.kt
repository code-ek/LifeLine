package com.lifeline.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sos
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeline.app.ui.theme.LifeLineFontFamily
import com.lifeline.app.R
import com.lifeline.app.core.ui.icon.LifeLineIcon
import com.lifeline.app.ui.theme.LifeLineMotion
import com.lifeline.app.ui.theme.LocalLifeLinePalette
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Building blocks for the redesigned About sheet.
 *
 * Kept in a separate file from [AboutSheet] because the sheet itself is mostly wiring for
 * preferences, whereas these are pure presentation.
 */

/** Horizontal inset shared by every About section, so cards and labels align to one grid. */
internal val AboutHorizontalPadding = 20.dp

/** Card corner radius for grouped rows. */
internal val AboutCardShape = RoundedCornerShape(16.dp)

/**
 * Two top-level views of the sheet: what the app is and how to drive it, versus the knobs.
 */
enum class AboutTab {
    Info,
    Settings,
}

/**
 * Small uppercase section label, e.g. `SETTINGS`.
 *
 * Uppercasing happens here rather than in the string resource so translators supply natural
 * sentence case and locales without a case distinction are unaffected.
 */
@Composable
internal fun AboutSectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalLifeLinePalette.current
    Text(
        text = text.uppercase(),
        fontFamily = LifeLineFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
        color = palette.textTertiary,
        modifier = modifier.padding(start = AboutHorizontalPadding, top = 24.dp, bottom = 8.dp)
    )
}

/**
 * Centered app identity block: logo, wordmark, tagline, version.
 */
@Composable
internal fun AboutHero(
    versionName: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val palette = LocalLifeLinePalette.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = LifeLineIcon,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.app_name),
            fontFamily = LifeLineFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            // Monospace at display size leaves too much air between glyphs; pull it in slightly
            // so the wordmark reads as a single unit.
            letterSpacing = (-0.5).sp,
            color = colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.about_tagline),
            fontFamily = LifeLineFontFamily,
            fontSize = 16.sp,
            color = colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.version_prefix, versionName),
            fontFamily = LifeLineFontFamily,
            fontSize = 12.sp,
            color = palette.textTertiary
        )
    }
}

/**
 * Two-up tab bar with a sliding underline indicator.
 *
 * The indicator animates its offset rather than cross-fading two static bars, which is what
 * makes the switch feel physically connected to the tap.
 */
@Composable
internal fun AboutTabBar(
    selected: AboutTab,
    onSelect: (AboutTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    var rowWidth by remember { mutableStateOf(0.dp) }
    val tabWidth = rowWidth / 2
    val indicatorOffset by animateDpAsState(
        targetValue = if (selected == AboutTab.Info) 0.dp else tabWidth,
        animationSpec = tween(LifeLineMotion.STANDARD_MS, easing = FastOutSlowInEasing),
        label = "aboutTabIndicator"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AboutHorizontalPadding)
            .onSizeChanged { size ->
                rowWidth = with(density) { size.width.toDp() }
            }
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            AboutTabLabel(
                text = stringResource(R.string.about_tab_info),
                isSelected = selected == AboutTab.Info,
                onClick = { onSelect(AboutTab.Info) },
                modifier = Modifier.weight(1f)
            )
            AboutTabLabel(
                text = stringResource(R.string.about_tab_settings),
                isSelected = selected == AboutTab.Settings,
                onClick = { onSelect(AboutTab.Settings) },
                modifier = Modifier.weight(1f)
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 1.dp, color = colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    // Lambda overload: the offset is animated every frame, and the non-lambda
                    // version would invalidate composition rather than just layout.
                    .offset { IntOffset(x = indicatorOffset.roundToPx(), y = 0) }
                    .width(tabWidth)
                    .height(2.dp)
                    .background(colorScheme.primary)
            )
        }
    }
}

@Composable
private fun AboutTabLabel(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    val color by animateColorAsState(
        targetValue = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
        animationSpec = tween(LifeLineMotion.QUICK_MS, easing = FastOutSlowInEasing),
        label = "aboutTabLabelColor"
    )

    Box(
        modifier = modifier
            .height(44.dp)
            .clickable(onClickLabel = text) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            fontFamily = LifeLineFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
            color = color
        )
    }
}

/** One line of the "How To Use" list: an icon plus a single instruction. */
@Composable
private fun AboutInstructionRow(
    icon: ImageVector,
    text: String
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AboutHorizontalPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(22.dp)
        )
        Text(
            text = text,
            fontFamily = LifeLineFontFamily,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = colorScheme.onSurface
        )
    }
}

/** The "How To Use" list: one line per screen of the app. */
@Composable
internal fun AboutHowToUseSection(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.about_how_to_use_heading),
            fontFamily = LifeLineFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.primary,
            modifier = Modifier.padding(
                start = AboutHorizontalPadding,
                top = 20.dp,
                bottom = 8.dp
            )
        )

        howToUse.forEach { (icon, text) -> AboutInstructionRow(icon = icon, text = text) }
    }
}

private val howToUse = listOf(
    Icons.Outlined.Groups to "Chat → Group chat talks to everyone nearby.",
    Icons.Outlined.Lock to "Chat → Private chats messages one person.",
    Icons.Outlined.Person to "Tap \u201cYou:\u201d to change your name or go anonymous.",
    Icons.Outlined.Sos to "SOS → tap Send SOS and confirm. Every phone nearby gets the alert.",
    Icons.Outlined.Download to "While you still have internet, download your area in Map and the AI in Helper.",
    Icons.Outlined.Share to "No internet? Share LifeLine with a nearby phone from Settings.",
    Icons.Outlined.BatteryChargingFull to "Keep LifeLine running in the background so messages can hop through your phone.",
)

private val features = listOf(
    Triple(
        Icons.Outlined.WifiOff,
        "Works without internet",
        "Messages hop from phone to phone over Bluetooth. No internet, signal or servers needed."
    ),
    Triple(
        Icons.Outlined.Sos,
        "SOS alerts",
        "Send an SOS with your location. Every LifeLine phone nearby passes it on."
    ),
    Triple(
        Icons.Outlined.Map,
        "Offline maps",
        "Download your area once and the map keeps working with no connection."
    ),
    Triple(
        Icons.Outlined.AutoAwesome,
        "Offline helper",
        "First aid and emergency answers from an AI that runs on your phone."
    ),
    Triple(
        Icons.Outlined.Translate,
        "Translator",
        "Talk with someone who speaks another language, even offline."
    ),
    Triple(
        Icons.Outlined.Lock,
        "Private by design",
        "Private chats are end-to-end encrypted. No accounts and no tracking."
    ),
)

/**
 * Capability list, laid out like [AboutHowToUseSection]: flat rows, no card surface or dividers.
 */
@Composable
internal fun AboutFeatureCard(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        features.forEach { (icon, title, subtitle) ->
            AboutFeatureRow(icon = icon, title = title, subtitle = subtitle)
        }
    }
}

@Composable
private fun AboutFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AboutHorizontalPadding, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(22.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontFamily = LifeLineFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 21.sp,
                color = colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontFamily = LifeLineFontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}
