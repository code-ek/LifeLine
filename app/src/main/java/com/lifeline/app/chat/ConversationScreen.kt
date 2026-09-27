package com.lifeline.app.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.lifeline.app.model.DeliveryStatus
import com.lifeline.app.model.LifeLineMessage
import com.lifeline.app.model.LifeLineMessageType
import com.lifeline.app.ui.ChatViewModel
import com.lifeline.app.ui.MessageInput
import com.lifeline.app.ui.media.AudioMessageItem
import com.lifeline.app.ui.media.ImageMessageItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One conversation: group chat (everyone nearby) when [privatePeer] is null, otherwise a private
 * chat. Messaging, voice notes and photos run through the existing chat engine.
 */
@Composable
fun ConversationScreen(
    viewModel: ChatViewModel,
    title: String,
    subtitle: String,
    messages: List<LifeLineMessage>,
    isMine: (LifeLineMessage) -> Boolean,
    privatePeer: String?,
    nickname: String,
    onBack: () -> Unit,
    talkingAs: String,
    onChangeIdentity: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf(TextFieldValue("")) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().background(scheme.background)) {
        Surface(color = scheme.surfaceContainerLowest) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                if (privatePeer != null) {
                    Avatar(title, size = 40.dp)
                    Spacer(Modifier.size(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                }
            }
        }
        IdentityBar(talkingAs = talkingAs, onChange = onChangeIdentity)
        HorizontalDivider(color = scheme.outlineVariant)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        if (privatePeer == null) "No messages yet. Say hello to everyone nearby!"
                        else "No messages yet. Only you two can read this chat.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }
            items(messages, key = { it.id }) { message ->
                when {
                    message.sender == "system" -> SystemLine(message.content)
                    else -> Bubble(
                        viewModel = viewModel,
                        message = message,
                        all = messages,
                        mine = isMine(message),
                        showSender = privatePeer == null,
                        nickname = nickname,
                        time = timeFormat.format(message.timestamp)
                    )
                }
            }
        }

        Surface(color = scheme.surfaceContainerLowest) {
            Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
                HorizontalDivider(color = scheme.outlineVariant)
                MessageInput(
                    value = text,
                    onValueChange = { text = it },
                    onSend = {
                        val content = text.text.trim()
                        if (content.isNotEmpty()) {
                            viewModel.sendMessage(content) { accepted -> if (accepted) text = TextFieldValue("") }
                        }
                    },
                    onSendVoiceNote = { peer, channel, path -> viewModel.sendVoiceNote(peer, channel, path) },
                    onSendImageNote = { peer, channel, path -> viewModel.sendImageNote(peer, channel, path) },
                    onSendFileNote = { peer, channel, path -> viewModel.sendFileNote(peer, channel, path) },
                    selectedPrivatePeer = privatePeer,
                    currentChannel = null,
                    nickname = nickname,
                    showMediaButtons = true,
                    recorderFactory = viewModel::createVoiceRecorder,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** "You're talking as …  Change" — always visible, so switching to anonymous is one tap away. */
@Composable
private fun IdentityBar(talkingAs: String, onChange: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.primaryContainer)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Avatar(talkingAs, size = 24.dp)
        Text(
            "You're talking as $talkingAs",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { focusManager.clearFocus(); onChange() }) { Text("Change") }
    }
}

@Composable
private fun SystemLine(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun Bubble(
    viewModel: ChatViewModel,
    message: LifeLineMessage,
    all: List<LifeLineMessage>,
    mine: Boolean,
    showSender: Boolean,
    nickname: String,
    time: String
) {
    val scheme = MaterialTheme.colorScheme
    val bubbleShape = if (mine) RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp) else RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp)
    val container = if (mine) scheme.primary else scheme.surfaceContainerLowest
    val content = if (mine) scheme.onPrimary else scheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine && showSender) {
            Avatar(message.sender, size = 34.dp)
            Spacer(Modifier.size(8.dp))
        }
        Column(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(bubbleShape)
                .background(container)
                .then(if (mine) Modifier else Modifier.border(1.dp, scheme.outlineVariant, bubbleShape))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (!mine && showSender) {
                Text(message.sender, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = avatarColor(message.sender))
            }
            when (message.type) {
                LifeLineMessageType.Audio -> viewModel.meshServiceFacade.let { mesh ->
                    AudioMessageItem(
                        message = message,
                        currentUserNickname = nickname,
                        meshService = mesh,
                        colorScheme = scheme,
                        timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault()),
                        onNicknameClick = null,
                        onMessageLongPress = null,
                        onCancelTransfer = { viewModel.cancelMediaSend(it.id) },
                        showSender = false,
                        bubbles = true
                    )
                }
                LifeLineMessageType.Image -> ImageMessageItem(
                    message = message,
                    messages = all,
                    currentUserNickname = nickname,
                    meshService = viewModel.meshServiceFacade,
                    colorScheme = scheme,
                    timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault()),
                    onNicknameClick = null,
                    onMessageLongPress = null,
                    onCancelTransfer = { viewModel.cancelMediaSend(it.id) },
                    onImageClick = null,
                    showSender = false,
                    bubbles = true
                )
                LifeLineMessageType.File -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = content)
                    Text(message.content.substringAfterLast('/'), style = MaterialTheme.typography.bodyLarge, color = content)
                }
                else -> Text(message.content, style = MaterialTheme.typography.bodyLarge, color = content)
            }
            Text(
                listOfNotNull(time, if (mine) statusLabel(message.deliveryStatus) else null).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

internal fun statusLabel(status: DeliveryStatus?): String? = when (status) {
    null -> null
    is DeliveryStatus.Sending -> "Sending…"
    is DeliveryStatus.Sent -> "Sent ✓"
    is DeliveryStatus.Delivered -> "Delivered ✓✓"
    is DeliveryStatus.Read -> "Read ✓✓"
    is DeliveryStatus.Failed -> "Not sent"
    is DeliveryStatus.PartiallyDelivered -> "Delivered to ${status.reached}/${status.total}"
}

internal fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
