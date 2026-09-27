package com.lifeline.app.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeline.app.ui.theme.ThemePreference
import com.lifeline.app.ui.theme.ThemePreferenceManager

/** Distinct, readable avatar colours (white text passes contrast on all of them). */
private val avatarColors = listOf(
    Color(0xFF1E7A35), Color(0xFF0062CC), Color(0xFF8E3FB5), Color(0xFFB35900),
    Color(0xFFB0304A), Color(0xFF00707A), Color(0xFF5A5FB0), Color(0xFF6B5B2E)
)

fun avatarColor(name: String): Color =
    if (Names.isAlias(name)) Color(0xFF5E6B5E)
    else avatarColors[(name.lowercase().hashCode() and 0x7fffffff) % avatarColors.size]

fun initialOf(name: String): String =
    if (Names.isAlias(name)) "?" else name.trim().firstOrNull()?.uppercase() ?: "?"

@Composable
fun Avatar(name: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(avatarColor(name)),
        contentAlignment = Alignment.Center
    ) {
        Text(initialOf(name), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.42f).sp)
    }
}

/** "You: Alex ▾" chip that opens the Talk-as sheet. */
@Composable
fun IdentityChip(label: String, name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Change how people see you", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Avatar(name, size = 26.dp)
        Text(
            "$label$name",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp)
        )
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

/** One tap between light and dark (the Settings screen still offers "System"). */
@Composable
fun ThemeToggleButton() {
    val context = LocalContext.current
    val pref by ThemePreferenceManager.themeFlow.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val dark = pref == ThemePreference.Dark || (pref == ThemePreference.System && systemDark)
    IconButton(onClick = {
        ThemePreferenceManager.set(context, if (dark) ThemePreference.Light else ThemePreference.Dark)
    }) {
        Icon(
            if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
            contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode"
        )
    }
}

/** Small tonal information card (e.g. "you can switch to anonymous any time"). */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier, icon: @Composable () -> Unit = {}) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        icon()
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
