package com.lifeline.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeline.app.assistant.AssistantScreen
import com.lifeline.app.emergency.EmergencyRuntime
import com.lifeline.app.emergency.EmergencyScreen
import com.lifeline.app.map.NearbyMapScreen
import com.lifeline.app.translate.TranslateScreen
import com.lifeline.app.chat.ChatNav
import com.lifeline.app.chat.ChatNav.isConversation
import com.lifeline.app.chat.ChatTab
import com.lifeline.app.ui.ChatViewModel

internal val SosRed = Color(0xFFD70015)

/** Top-level app layout: one screen per feature, switched by a bottom navigation bar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppShell(chatViewModel: ChatViewModel) {
    val tab by AppNavigation.tab.collectAsStateWithLifecycle()
    val alerts by EmergencyRuntime.alerts.collectAsStateWithLifecycle()
    val hasIncomingSos = alerts.any { !it.isLocal }
    // Keep the bar out of the way while typing so the chat input sits on the keyboard as before.
    val chatRoute by ChatNav.route.collectAsStateWithLifecycle()
    // Conversations get the whole screen; elsewhere the bar hides only while typing.
    val showBar = !WindowInsets.isImeVisible && !(tab == AppTab.CHAT && chatRoute.isConversation)
    val stateHolder = rememberSaveableStateHolder()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // The bar owns the bottom system inset while it is shown.
                .then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)
        ) {
            stateHolder.SaveableStateProvider(tab.name) {
                when (tab) {
                    AppTab.CHAT -> ChatTab(viewModel = chatViewModel)
                    AppTab.MAP -> NearbyMapScreen()
                    AppTab.SOS -> EmergencyScreen()
                    AppTab.TRANSLATE -> TranslateScreen()
                    AppTab.AI -> AssistantScreen()
                }
            }
        }
        if (showBar) {
            AppBottomBar(selected = tab, hasIncomingSos = hasIncomingSos, onSelect = AppNavigation::select)
        }
    }
}

@Composable
private fun AppBottomBar(selected: AppTab, hasIncomingSos: Boolean, onSelect: (AppTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        TabItem(AppTab.CHAT, selected, "Chat", Icons.Filled.Forum, Icons.Outlined.Forum, onSelect)
        TabItem(AppTab.MAP, selected, "Map", Icons.Filled.Map, Icons.Outlined.Map, onSelect)
        NavigationBarItem(
            selected = selected == AppTab.SOS,
            onClick = { onSelect(AppTab.SOS) },
            icon = {
                BadgedBox(badge = { if (hasIncomingSos) Badge(containerColor = Color(0xFFFF9500)) }) {
                    Box(
                        modifier = Modifier.size(28.dp).background(SosRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("SOS", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            },
            label = { Text("SOS", color = SosRed, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = SosRed.copy(alpha = 0.15f))
        )
        TabItem(AppTab.TRANSLATE, selected, "Translate", Icons.Filled.Translate, Icons.Outlined.Translate, onSelect)
        TabItem(AppTab.AI, selected, "Helper", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, onSelect)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    tab: AppTab,
    selected: AppTab,
    label: String,
    selectedIcon: ImageVector,
    icon: ImageVector,
    onSelect: (AppTab) -> Unit
) {
    val isSelected = tab == selected
    NavigationBarItem(
        selected = isSelected,
        onClick = { onSelect(tab) },
        icon = { Icon(if (isSelected) selectedIcon else icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}
