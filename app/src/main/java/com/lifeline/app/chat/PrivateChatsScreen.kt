package com.lifeline.app.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A private chat in the list. */
data class PrivateChatRow(
    val id: String,
    val name: String,
    val preview: String,
    val time: String,
    val unread: Int
)

/** Someone nearby you can start a private chat with. */
data class NearbyPerson(val peerID: String, val name: String)

@Composable
fun PrivateChatsScreen(
    chats: List<PrivateChatRow>,
    nearby: List<NearbyPerson>,
    onOpen: (id: String, name: String) -> Unit,
    onBack: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(scheme.background)) {
        Surface(color = scheme.surfaceContainerLowest) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Column {
                    Text("Private chats", style = MaterialTheme.typography.titleLarge)
                    Text("Only you two can read", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(color = scheme.outlineVariant)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (chats.isEmpty()) {
                item {
                    Text(
                        "No private chats yet. Pick someone below to start one.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            items(chats, key = { it.id }) { chat -> ChatRowItem(chat) { onOpen(chat.id, chat.name) } }

            item {
                Text(
                    "Start a private chat with someone nearby",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                )
            }
            item {
                if (nearby.isEmpty()) {
                    Text("No one nearby right now.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(nearby, key = { it.peerID }) { person ->
                            Column(
                                modifier = Modifier
                                    .width(76.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClickLabel = "Message ${person.name}") { onOpen(person.peerID, person.name) }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Avatar(person.name, size = 58.dp)
                                Text(person.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            item {
                InfoNote(
                    "Private chats are locked. Nobody else can read them, not even the phones passing them along.",
                    modifier = Modifier.padding(top = 12.dp),
                    icon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = scheme.onPrimaryContainer) }
                )
            }
        }
    }
}

@Composable
private fun ChatRowItem(chat: PrivateChatRow, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(shape)
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant, shape)
            .clickable(onClickLabel = "Open chat with ${chat.name}", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Avatar(chat.name, size = 50.dp)
        Column(Modifier.weight(1f)) {
            Row {
                Text(chat.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(chat.time, style = MaterialTheme.typography.labelMedium, color = if (chat.unread > 0) scheme.primary else scheme.onSurfaceVariant)
            }
            Text(
                chat.preview,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (chat.unread > 0) FontWeight.Bold else FontWeight.Normal,
                color = if (chat.unread > 0) scheme.onSurface else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (chat.unread > 0) {
            Box(
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(13.dp)).background(scheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(chat.unread.toString(), style = MaterialTheme.typography.labelMedium, color = scheme.onPrimary)
            }
        } else {
            Spacer(Modifier.size(0.dp))
        }
    }
}
