package com.lifeline.app.chat

import com.lifeline.app.chat.ChatNav.isConversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatNavTest {

    @Before
    fun reset() {
        while (ChatNav.back()) Unit
    }

    @Test
    fun backWalksPrivateChatToListToHome() {
        ChatNav.open(ChatRoute.Private("peer1", "Maya"))
        assertTrue(ChatNav.back())
        assertEquals(ChatRoute.PrivateList, ChatNav.route.value)
        assertTrue(ChatNav.back())
        assertEquals(ChatRoute.Home, ChatNav.route.value)
        assertFalse(ChatNav.back())
    }

    @Test
    fun groupGoesStraightHome() {
        ChatNav.open(ChatRoute.Group)
        assertTrue(ChatNav.back())
        assertEquals(ChatRoute.Home, ChatNav.route.value)
    }

    @Test
    fun onlyConversationsHideTheBottomBar() {
        assertTrue(ChatRoute.Group.isConversation)
        assertTrue(ChatRoute.Private("p", "t").isConversation)
        assertFalse(ChatRoute.Home.isConversation)
        assertFalse(ChatRoute.PrivateList.isConversation)
    }

    @Test
    fun groupSeenOnlyMovesForward() {
        ChatNav.markGroupSeen(2_000)
        ChatNav.markGroupSeen(1_000)
        assertEquals(2_000L, ChatNav.groupSeenAt.value)
    }
}
