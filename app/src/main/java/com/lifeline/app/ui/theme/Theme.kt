package com.lifeline.app.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

// Standard UI semantics live in Material so stock components and custom LifeLine composables
// share one source of truth. LocalLifeLinePalette below only supplies app-specific extra colors.
internal val DarkLifeLineColorScheme = darkColorScheme(
    primary = Color(0xFF32D74B),
    onPrimary = Color(0xFF002A0B),
    primaryContainer = Color(0xFF163D1D),
    onPrimaryContainer = Color(0xFFB8F5C1),
    secondary = Color(0xFF5AC8FA),
    onSecondary = Color(0xFF003544),
    secondaryContainer = Color(0xFF0F3A3F),
    onSecondaryContainer = Color(0xFFB8EEF5),
    tertiary = Color(0xFFFFD60A),
    onTertiary = Color(0xFF2C2200),
    tertiaryContainer = Color(0xFF3A3000),
    onTertiaryContainer = Color(0xFFFFEA80),
    background = Color(0xFF101A12),
    onBackground = Color(0xFFE8F0E8),
    surface = Color(0xFF101A12),
    onSurface = Color(0xFFE8F0E8),
    surfaceVariant = Color(0xFF1F2E23),
    onSurfaceVariant = Color(0xFFA3B0A3),
    surfaceContainerLowest = Color(0xFF0A1310),
    surfaceContainerLow = Color(0xFF142018),
    surfaceContainer = Color(0xFF18251C),
    surfaceContainerHigh = Color(0xFF1F2E23),
    surfaceContainerHighest = Color(0xFF263829),
    outline = Color(0xFF5E6B5E),
    outlineVariant = Color(0xFF2E4032),
    error = Color(0xFFFF6961),
    onError = Color(0xFF2D0000),
    errorContainer = Color(0xFF3D1418),
    onErrorContainer = Color(0xFFFFB4AB)
)

internal val LightLifeLineColorScheme = lightColorScheme(
    primary = Color(0xFF248A3D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F1D8),
    onPrimaryContainer = Color(0xFF0A3212),
    secondary = Color(0xFF007AFF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E9FF),
    onSecondaryContainer = Color(0xFF002C5C),
    tertiary = Color(0xFFE8A317),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF3D6),
    onTertiaryContainer = Color(0xFF3A2800),
    background = Color(0xFFF4F8F4),
    onBackground = Color(0xFF131A13),
    surface = Color(0xFFF4F8F4),
    onSurface = Color(0xFF131A13),
    surfaceVariant = Color(0xFFE7EDE7),
    onSurfaceVariant = Color(0xFF4C574C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFCFA),
    surfaceContainer = Color(0xFFEEF3EE),
    surfaceContainerHigh = Color(0xFFE7EDE7),
    surfaceContainerHighest = Color(0xFFDEE6DE),
    outline = Color(0xFF7A867A),
    outlineVariant = Color(0xFFDEE6DE),
    error = Color(0xFFD70015),
    onError = Color.White
)

/** Gently rounded, not bubbly: cards 12 dp, big buttons and panels 16 dp. */
internal val LifeLineShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun LifeLineTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    // App-level override from ThemePreferenceManager
    val themePref by ThemePreferenceManager.themeFlow.collectAsState(initial = ThemePreference.System)
    val shouldUseDark = when (darkTheme) {
        true -> true
        false -> false
        null -> when (themePref) {
            ThemePreference.Dark -> true
            ThemePreference.Light -> false
            ThemePreference.System -> isSystemInDarkTheme()
        }
    }

    val colorScheme = if (shouldUseDark) DarkLifeLineColorScheme else LightLifeLineColorScheme
    val palette = if (shouldUseDark) DarkLifeLinePalette else LightLifeLinePalette

    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    if (!shouldUseDark) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (!shouldUseDark) {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else 0
            }
            window.navigationBarColor = colorScheme.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    CompositionLocalProvider(LocalLifeLinePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = LifeLineShapes,
            content = content
        )
    }
}
