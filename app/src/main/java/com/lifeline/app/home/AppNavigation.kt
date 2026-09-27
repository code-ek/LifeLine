package com.lifeline.app.home

import android.content.Context
import android.content.Intent
import com.lifeline.app.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTab { CHAT, MAP, SOS, TRANSLATE, AI }

/** Selected bottom-bar tab. Process-wide so notifications and back handling can drive it. */
object AppNavigation {
    const val EXTRA_OPEN_TAB = "com.lifeline.app.OPEN_TAB"

    private val _tab = MutableStateFlow(AppTab.CHAT)
    val tab: StateFlow<AppTab> = _tab.asStateFlow()

    fun select(tab: AppTab) {
        _tab.value = tab
    }

    /** Back from any other tab returns to Chat. Returns true if it consumed the back press. */
    fun handleBack(): Boolean {
        if (_tab.value == AppTab.CHAT) return com.lifeline.app.chat.ChatNav.back()
        _tab.value = AppTab.CHAT
        return true
    }

    fun handleIntent(intent: Intent?) {
        intent ?: return
        val requested = intent.getStringExtra(EXTRA_OPEN_TAB)
            ?.let { name -> AppTab.entries.firstOrNull { it.name == name } }
        when {
            requested != null -> select(requested)
            intent.getBooleanExtra(com.lifeline.app.ui.NotificationManager.EXTRA_OPEN_PRIVATE_CHAT, false) ->
                select(AppTab.CHAT)
        }
    }

    fun intentFor(context: Context, tab: AppTab): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_TAB, tab.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
