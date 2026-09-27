package com.lifeline.app.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeline.app.model.LifeLineMessage
import com.lifeline.app.ui.AboutSheet
import com.lifeline.app.ui.ChatViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * The Chat tab: a simple home with Group chat and Private chats. All messaging goes through
 * the existing [ChatViewModel] (mesh, encryption, delivery), only the screens are new.
 */
@Composable
fun ChatTab(viewModel: ChatViewModel) {
    val context = LocalContext.current
    IdentityStore.init(context)
    val identity by IdentityStore.state.collectAsStateWithLifecycle()
    val route by ChatNav.route.collectAsStateWithLifecycle()
    val nickname by viewModel.nickname.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val privateChats by viewModel.privateChats.collectAsStateWithLifecycle()
    val selectedPeer by viewModel.selectedPrivateChatPeer.collectAsStateWithLifecycle()
    val unreadPrivate by viewModel.unreadPrivateMessages.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val peerNicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
    val showAppInfo by viewModel.showAppInfo.collectAsStateWithLifecycle()
    val groupSeenAt by ChatNav.groupSeenAt.collectAsStateWithLifecycle()
    var showIdentity by remember { mutableStateOf(false) }

    // Everyone sees the name (or alias) chosen here.
    LaunchedEffect(identity.displayName, identity.setupDone) {
        if (identity.setupDone && nickname != identity.displayName) viewModel.setNickname(identity.displayName)
    }

    // Keep the engine's "current conversation" in step with the screen.
    LaunchedEffect(route) {
        when (val r = route) {
            is ChatRoute.Private -> viewModel.startPrivateChat(r.peerID)
            ChatRoute.Group -> {
                if (viewModel.selectedPrivateChatPeer.value != null) viewModel.endPrivateChat()
                viewModel.switchToChannel(null)
            }
            else -> if (viewModel.selectedPrivateChatPeer.value != null) viewModel.endPrivateChat()
        }
    }

    val myPeerID = viewModel.myPeerID
    val ownNames = identity.ownNames + nickname
    val isMine: (LifeLineMessage) -> Boolean = { it.senderPeerID == myPeerID || it.sender in ownNames }
    val groupMessages = messages.filter { !it.isPrivate && it.channel == null }
    val nearby = connectedPeers.filter { it != myPeerID }

    if (route == ChatRoute.Group) {
        LaunchedEffect(groupMessages.lastOrNull()?.timestamp) {
            groupMessages.lastOrNull()?.let { ChatNav.markGroupSeen(it.timestamp.time) }
        }
    }

    when (val r = route) {
        ChatRoute.Home -> {
            val latestGroup = groupMessages.lastOrNull { it.sender != "system" }
            val latestPrivate = conversations.firstOrNull()
            ChatHomeScreen(
                displayName = identity.displayName,
                peopleNearby = nearby.size,
                groupUnread = groupMessages.count { it.timestamp.time > groupSeenAt && it.sender != "system" && !isMine(it) },
                groupPreview = latestGroup?.let { "${if (isMine(it)) "You" else it.sender}: ${previewOf(it)}" },
                privateUnread = unreadPrivate.size,
                privatePreview = latestPrivate?.let { "${it.displayName}: ${it.latestMessagePreview}" },
                onOpenGroup = { ChatNav.open(ChatRoute.Group) },
                onOpenPrivate = { ChatNav.open(ChatRoute.PrivateList) },
                onIdentity = { showIdentity = true },
                onSettings = { viewModel.showAppInfo() }
            )
        }
        ChatRoute.Group -> ConversationScreen(
            viewModel = viewModel,
            title = "Group chat",
            subtitle = when (nearby.size) { 0 -> "No one nearby yet"; 1 -> "1 person nearby"; else -> "${nearby.size} people nearby" },
            messages = groupMessages,
            isMine = isMine,
            privatePeer = null,
            nickname = nickname,
            onBack = { ChatNav.back() },
            talkingAs = identity.displayName,
            onChangeIdentity = { showIdentity = true }
        )
        ChatRoute.PrivateList -> PrivateChatsScreen(
            chats = conversations.map {
                PrivateChatRow(
                    id = it.conversationID,
                    name = it.displayName,
                    preview = (if (it.latestMessageIsOutgoing) "You: " else "") + it.latestMessagePreview,
                    time = formatTime(it.latestMessageAt),
                    unread = it.unreadCount
                )
            },
            nearby = nearby.map { NearbyPerson(it, peerNicknames[it] ?: "Someone nearby") },
            onOpen = { id, name -> ChatNav.open(ChatRoute.Private(id, name)) },
            onBack = { ChatNav.back() }
        )
        is ChatRoute.Private -> {
            val key = selectedPeer ?: r.peerID
            ConversationScreen(
                viewModel = viewModel,
                title = r.title,
                subtitle = if (key in connectedPeers || r.peerID in connectedPeers) "Nearby now · locked chat" else "Locked chat · delivers when nearby",
                messages = privateChats[key] ?: privateChats[r.peerID].orEmpty(),
                isMine = isMine,
                privatePeer = key,
                nickname = nickname,
                onBack = { ChatNav.back() },
                talkingAs = identity.displayName,
                onChangeIdentity = { showIdentity = true }
            )
        }
    }

    if (showIdentity) IdentitySheet(identity = identity, onDismiss = { showIdentity = false })
    AboutSheet(isPresented = showAppInfo, onDismiss = { viewModel.hideAppInfo() })
}

private fun previewOf(message: LifeLineMessage): String = when (message.type) {
    com.lifeline.app.model.LifeLineMessageType.Audio -> "Voice message"
    com.lifeline.app.model.LifeLineMessageType.Image -> "Photo"
    com.lifeline.app.model.LifeLineMessageType.File -> "File"
    else -> message.content
}
