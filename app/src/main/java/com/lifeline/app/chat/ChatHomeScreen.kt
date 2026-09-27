package com.lifeline.app.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Chat tab home: who's nearby, then two big doors — Group chat and Private chats. */
@Composable
fun ChatHomeScreen(
    displayName: String,
    peopleNearby: Int,
    groupUnread: Int,
    groupPreview: String?,
    privateUnread: Int,
    privatePreview: String?,
    onOpenGroup: () -> Unit,
    onOpenPrivate: () -> Unit,
    onIdentity: () -> Unit,
    onSettings: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Chats", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            ThemeToggleButton()
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Settings & about") }, onClick = { menuOpen = false; onSettings() })
                }
            }
        }

        IdentityChip(label = "You: ", name = displayName, onClick = onIdentity)
        NearbyPill(peopleNearby)

        DoorCard(
            icon = Icons.Filled.Groups,
            title = "Group chat",
            subtitle = "Everyone nearby can read",
            unread = groupUnread,
            preview = groupPreview,
            filled = true,
            onClick = onOpenGroup
        )
        DoorCard(
            icon = Icons.Filled.Lock,
            title = "Private chats",
            subtitle = "Only you two can read",
            unread = privateUnread,
            preview = privatePreview,
            filled = false,
            onClick = onOpenPrivate
        )
        if (peopleNearby == 0) {
            InfoNote("No one nearby yet. Phones with LifeLine show up when close, no internet needed.")
        }
    }
}

@Composable
private fun NearbyPill(count: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (count > 0) scheme.primaryContainer else scheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (count > 0) scheme.primary else scheme.outline))
        Text(
            when (count) { 0 -> "No one nearby"; 1 -> "1 person nearby"; else -> "$count people nearby" },
            style = MaterialTheme.typography.labelLarge,
            color = if (count > 0) scheme.onPrimaryContainer else scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DoorCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    unread: Int,
    preview: String?,
    filled: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val bg = if (filled) scheme.primaryContainer else scheme.surfaceContainerLowest
    val fg = if (filled) scheme.onPrimaryContainer else scheme.onSurface
    val sub = if (filled) scheme.onPrimaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
            .clip(shape)
            .background(bg)
            .then(if (filled) Modifier else Modifier.border(2.dp, scheme.outlineVariant, shape))
            .clickable(onClickLabel = "Open $title", onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (filled) scheme.primary.copy(alpha = 0.18f) else scheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (filled) scheme.primary else scheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = fg)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = sub)
            }
            if (unread > 0) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (filled) scheme.primary else scheme.primary)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (unread > 99) "99+" else unread.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (filled) scheme.onPrimary else scheme.onPrimary
                    )
                }
            }
        }
        Text(
            preview ?: if (filled) "Tap to talk with everyone nearby" else "Tap to message one person",
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (filled) scheme.primary.copy(alpha = 0.12f) else scheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
        Spacer(Modifier.size(0.dp))
    }
}
