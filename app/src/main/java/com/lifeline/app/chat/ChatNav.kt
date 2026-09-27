package com.lifeline.app.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ChatRoute {
    data object Home : ChatRoute
    data object Group : ChatRoute
    data object PrivateList : ChatRoute
    data class Private(val peerID: String, val title: String) : ChatRoute
}

/** Where the Chat tab is. Process-wide so the system back button can walk it. */
object ChatNav {
    private val _route = MutableStateFlow<ChatRoute>(ChatRoute.Home)
    val route: StateFlow<ChatRoute> = _route.asStateFlow()

    /** Newest group message the user has seen, for the unread count on the home card. */
    private val _groupSeenAt = MutableStateFlow(0L)
    val groupSeenAt: StateFlow<Long> = _groupSeenAt.asStateFlow()

    fun open(route: ChatRoute) {
        _route.value = route
    }

    fun markGroupSeen(atMs: Long) {
        if (atMs > _groupSeenAt.value) _groupSeenAt.value = atMs
    }

    /** One step back; false when already on the Chat home (let the app handle it). */
    fun back(): Boolean {
        _route.value = when (_route.value) {
            ChatRoute.Home -> return false
            ChatRoute.Group, ChatRoute.PrivateList -> ChatRoute.Home
            is ChatRoute.Private -> ChatRoute.PrivateList
        }
        return true
    }

    /** Conversation screens hide the bottom bar so the typing box has room. */
    val ChatRoute.isConversation: Boolean get() = this is ChatRoute.Group || this is ChatRoute.Private
}
