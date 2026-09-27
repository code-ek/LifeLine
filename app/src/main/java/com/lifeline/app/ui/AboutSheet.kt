package com.lifeline.app.ui

import kotlinx.coroutines.launch
import com.lifeline.app.util.AppShare
import com.lifeline.app.util.AppShareResult
import com.lifeline.app.util.ShareableApp
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Security
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeline.app.ui.theme.LifeLineFontFamily
import com.lifeline.app.R
import com.lifeline.app.core.ui.component.button.CloseButton
import com.lifeline.app.core.ui.component.sheet.LocalSheetDismiss
import com.lifeline.app.core.ui.component.sheet.LifeLineBottomSheet
import com.lifeline.app.hotspot.HotspotActivity
import com.lifeline.app.ui.theme.LifeLineMotion
import com.lifeline.app.ui.theme.LocalLifeLinePalette

/**
 * Theme selection chip
 */
@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    // Cross-fade the chip so switching theme does not read as two separate flashes (the chip
    // recolouring plus the whole app recolouring underneath it).
    val containerColor by animateColorAsState(
        targetValue = if (selected) colorScheme.primary else colorScheme.surfaceVariant,
        animationSpec = tween(LifeLineMotion.STANDARD_MS, easing = FastOutSlowInEasing),
        label = "themeChipContainer"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) Color.White else colorScheme.onSurfaceVariant,
        animationSpec = tween(LifeLineMotion.STANDARD_MS, easing = FastOutSlowInEasing),
        label = "themeChipLabel"
    )

    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = containerColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontFamily = LifeLineFontFamily,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = labelColor
            )
        }
    }
}

@Composable
private fun LanguageSettingsRow(
    selectedLanguageName: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.about_app_language),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = selectedLanguageName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Filled.UnfoldMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LanguageMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        onClick = onClick,
        trailingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

/**
 * Unified settings toggle row with icon, title, subtitle, and switch
 * Settings section with consistent spacing
 */
@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    statusIndicator: (@Composable () -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val palette = LocalLifeLinePalette.current
    val interactionSource = remember { MutableInteractionSource() }

    // Colours cross-fade so a row becoming available eases in rather
    // than popping.
    val iconTint by animateColorAsState(
        targetValue = if (enabled) colorScheme.primary else palette.textTertiary,
        animationSpec = tween(LifeLineMotion.STANDARD_MS, easing = FastOutSlowInEasing),
        label = "settingsRowIcon"
    )
    val titleColor by animateColorAsState(
        targetValue = if (enabled) colorScheme.onSurface else palette.textTertiary,
        animationSpec = tween(LifeLineMotion.STANDARD_MS, easing = FastOutSlowInEasing),
        label = "settingsRowTitle"
    )
    val subtitleColor by animateColorAsState(
        targetValue = if (enabled) colorScheme.onSurfaceVariant else palette.textTertiary,
        animationSpec = tween(LifeLineMotion.STANDARD_MS, easing = FastOutSlowInEasing),
        label = "settingsRowSubtitle"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row toggles, not just the switch: a 14.dp-tall switch is a poor target
            // when there is a full-width row sitting right next to it.
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontFamily = LifeLineFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = titleColor
                )
                statusIndicator?.invoke()
            }
            Text(
                text = subtitle,
                fontFamily = LifeLineFontFamily,
                fontSize = 12.sp,
                color = subtitleColor,
                lineHeight = 17.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Switch(
            checked = checked,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
            enabled = enabled,
            interactionSource = interactionSource,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colorScheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = colorScheme.surfaceVariant
            )
        )
    }
}

/**
 * About / Settings sheet: what LifeLine does and how to use it, plus its settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Get version name from package info
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "1.0.0" // fallback version
        }
    }

    val lazyListState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0
        }
    }
    val topBarAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 0.98f else 0f,
        animationSpec = tween(LifeLineMotion.EMPHASIZED_MS, easing = FastOutSlowInEasing),
        label = "topBarAlpha"
    )

    val colorScheme = MaterialTheme.colorScheme
    val palette = LocalLifeLinePalette.current
    var selectedTab by remember { mutableStateOf(AboutTab.Info) }
    val supportedLanguages = remember(context) {
        LanguagePreferenceManager.supportedLanguages(context)
    }
    var selectedLanguageTag by remember {
        mutableStateOf(LanguagePreferenceManager.currentLanguageTag())
    }
    var showLanguagePicker by remember { mutableStateOf(false) }

    if (isPresented) {
        LifeLineBottomSheet(
            modifier = modifier,
            onDismissRequest = onDismiss,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 72.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Header Section - App Identity
                    item(key = "hero") {
                        AboutHero(versionName = versionName ?: "")
                    }

                    item(key = "tabs") {
                        AboutTabBar(
                            selected = selectedTab,
                            onSelect = { selectedTab = it },
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    }

                    if (selectedTab == AboutTab.Info) {
                        // What the app is, then how to drive it. Both are reference material a
                        // new user reads once, so they belong on the same tab.
                        item(key = "features") {
                            Column {
                                AboutSectionLabel(text = stringResource(R.string.about_section_about))
                                AboutFeatureCard()
                            }
                        }

                        item(key = "how_to_use") {
                            AboutHowToUseSection()
                        }
                    }

                    if (selectedTab == AboutTab.Settings) {
                    // Appearance Section
                    item(key = "appearance") {
                        Column {
                            AboutSectionLabel(text = stringResource(R.string.about_section_theme))
                            val themePref by com.lifeline.app.ui.theme.ThemePreferenceManager.themeFlow.collectAsState()
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AboutHorizontalPadding),
                                color = colorScheme.surface,
                                shape = AboutCardShape
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ThemeChip(
                                            label = stringResource(R.string.about_system),
                                            selected = themePref.isSystem,
                                            onClick = { com.lifeline.app.ui.theme.ThemePreferenceManager.set(context, com.lifeline.app.ui.theme.ThemePreference.System) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        ThemeChip(
                                            label = stringResource(R.string.about_light),
                                            selected = themePref.isLight,
                                            onClick = { com.lifeline.app.ui.theme.ThemePreferenceManager.set(context, com.lifeline.app.ui.theme.ThemePreference.Light) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        ThemeChip(
                                            label = stringResource(R.string.about_dark),
                                            selected = themePref.isDark,
                                            onClick = { com.lifeline.app.ui.theme.ThemePreferenceManager.set(context, com.lifeline.app.ui.theme.ThemePreference.Dark) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "language") {
                        val selectedLanguageName = supportedLanguages
                            .firstOrNull { it.languageTag == selectedLanguageTag }
                            ?.endonym
                            ?: stringResource(R.string.about_system_default)

                        Column {
                            AboutSectionLabel(text = stringResource(R.string.about_language))
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AboutHorizontalPadding),
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = colorScheme.surface,
                                    shape = AboutCardShape,
                                ) {
                                    LanguageSettingsRow(
                                        selectedLanguageName = selectedLanguageName,
                                        onClick = { showLanguagePicker = true },
                                    )
                                }
                                DropdownMenu(
                                    expanded = showLanguagePicker,
                                    onDismissRequest = { showLanguagePicker = false },
                                    modifier = Modifier.width(maxWidth),
                                ) {
                                    LanguageMenuItem(
                                        label = stringResource(R.string.about_system_default),
                                        selected = selectedLanguageTag.isEmpty(),
                                        onClick = {
                                            showLanguagePicker = false
                                            if (selectedLanguageTag.isNotEmpty()) {
                                                selectedLanguageTag = ""
                                                LanguagePreferenceManager.setLanguage("")
                                            }
                                        },
                                    )
                                    HorizontalDivider()
                                    supportedLanguages.forEach { language ->
                                        LanguageMenuItem(
                                            label = language.endonym,
                                            selected = selectedLanguageTag == language.languageTag,
                                            onClick = {
                                                showLanguagePicker = false
                                                if (language.languageTag != selectedLanguageTag) {
                                                    selectedLanguageTag = language.languageTag
                                                    LanguagePreferenceManager.setLanguage(language.languageTag)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Settings Section - Unified Card with Toggles
                    item(key = "settings") {
                        var backgroundEnabled by remember { mutableStateOf(com.lifeline.app.service.MeshServicePreferences.isBackgroundEnabled(true)) }
                        var liveVoiceEnabled by remember {
                            mutableStateOf(com.lifeline.app.features.voice.LiveVoicePreferences.isEnabled(context))
                        }

                        Column {
                            AboutSectionLabel(text = stringResource(R.string.about_section_settings))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AboutHorizontalPadding),
                                color = colorScheme.surface,
                                shape = AboutCardShape
                            ) {
                                Column {
                                    // Background Mode Toggle
                                    SettingsToggleRow(
                                        icon = Icons.Filled.Bluetooth,
                                        title = stringResource(R.string.about_background_title),
                                        subtitle = stringResource(R.string.about_background_desc),
                                        checked = backgroundEnabled,
                                        onCheckedChange = { enabled ->
                                            backgroundEnabled = enabled
                                            com.lifeline.app.service.MeshServicePreferences.setBackgroundEnabled(enabled)
                                            if (!enabled) {
                                                com.lifeline.app.service.MeshForegroundService.stop(context)
                                            } else {
                                                com.lifeline.app.service.MeshForegroundService.start(context)
                                            }
                                        }
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 54.dp),
                                        thickness = 1.dp,
                                        color = colorScheme.outlineVariant
                                    )

                                    SettingsToggleRow(
                                        icon = Icons.Filled.Mic,
                                        title = "Live push-to-talk",
                                        subtitle = "Play voice bursts live on the mesh; voice notes are always sent on release",
                                        checked = liveVoiceEnabled,
                                        onCheckedChange = { enabled ->
                                            liveVoiceEnabled = enabled
                                            com.lifeline.app.features.voice.LiveVoicePreferences.setEnabled(context, enabled)
                                        }
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 54.dp),
                                        thickness = 1.dp,
                                        color = colorScheme.outlineVariant
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 56.dp),
                                        color = colorScheme.outline.copy(alpha = 0.12f)
                                    )

                                    // === Share LifeLine offline, phone to phone ===
                                    ShareLifeLineRows()

                                }
                            }

                        }
                    }

                    } // end Settings tab

                    // Footer
                    item(key = "footer") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = AboutHorizontalPadding,
                                    end = AboutHorizontalPadding,
                                    top = 24.dp
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Works when the network doesn't",
                                fontSize = 11.sp,
                                fontFamily = LifeLineFontFamily,
                                color = palette.textTertiary
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }

                // TopBar
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(colorScheme.background.copy(alpha = topBarAlpha))
                ) {
                    val dismiss = LocalSheetDismiss.current
                    CloseButton(
                        onClick = { dismiss?.invoke() ?: onDismiss() },
                        modifier = modifier
                            .align(Alignment.CenterEnd)
                            .padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

/** Two ways to pass LifeLine to a phone that doesn't have it, with no internet needed. */
@Composable
private fun ShareLifeLineRows() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun withApp(action: (ShareableApp) -> Unit) {
        if (busy) return
        busy = true
        problem = null
        scope.launch {
            when (val result = AppShare.prepare(context)) {
                is AppShareResult.Ready -> action(result.app)
                is AppShareResult.Unavailable -> problem = result.reason
            }
            busy = false
        }
    }

    ShareRow(
        icon = Icons.Default.Wifi,
        title = stringResource(R.string.hotspot_share_via),
        subtitle = stringResource(R.string.hotspot_share_via_subtitle),
        enabled = !busy
    ) {
        withApp { app ->
            context.startActivity(
                Intent(context, HotspotActivity::class.java).putExtra(HotspotActivity.EXTRA_APK_PATH, app.file.path)
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = colorScheme.outline.copy(alpha = 0.12f))
    ShareRow(
        icon = Icons.Default.Bluetooth,
        title = stringResource(R.string.hotspot_share_other),
        subtitle = stringResource(R.string.hotspot_share_other_subtitle),
        enabled = !busy
    ) {
        withApp { app -> context.startActivity(AppShare.shareIntent(context, app)) }
    }
    problem?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.error,
            modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 12.dp)
        )
    }
}

@Composable
private fun ShareRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurface.copy(alpha = 0.6f), lineHeight = 16.sp)
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
    }
}
