package com.lifeline.app.chat

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/** Who you appear as in chat: your chosen name, or an anonymous alias. */
data class IdentityState(
    val name: String,
    val anonymous: Boolean,
    val alias: String,
    val setupDone: Boolean
) {
    val displayName: String get() = if (anonymous || name.isBlank()) alias else name

    /** Every name this phone has chatted under, so its own messages stay "mine" after a switch. */
    val ownNames: Set<String> get() = setOfNotNull(name.takeIf { it.isNotBlank() }, alias)
}

/** Name rules and generators. Pure, so they are unit tested. */
object Names {
    const val MAX_LENGTH = 24

    private val adjectives = listOf(
        "Brave", "Calm", "Kind", "Quick", "Bright", "Swift", "Gentle", "Bold",
        "Happy", "Lucky", "Sunny", "Clever", "Steady", "Wise", "Cheerful", "Loyal"
    )
    private val animals = listOf(
        "Otter", "Falcon", "Panda", "Tiger", "Dolphin", "Fox", "Eagle", "Koala",
        "Wolf", "Owl", "Lynx", "Heron", "Bison", "Robin", "Seal", "Deer"
    )

    fun random(random: Random = Random.Default): String =
        "${adjectives[random.nextInt(adjectives.size)]} ${animals[random.nextInt(animals.size)]}"

    /** Short anonymous alias, e.g. "Anon2783". */
    fun anonymousAlias(random: Random = Random.Default): String = "Anon${random.nextInt(1000, 10000)}"

    private val aliasPattern = Regex("Anon\\d{4}")
    private val oldAliasPattern = Regex("Anonymous (\\d{4})")

    fun isAlias(name: String): Boolean = aliasPattern.matches(name)

    /** Upgrades the earlier "Anonymous 2783" form to "Anon2783"; other names pass through. */
    fun migrateAlias(alias: String): String =
        oldAliasPattern.matchEntire(alias)?.let { "Anon${it.groupValues[1]}" } ?: alias

    /** Trims, removes control characters, collapses spaces and caps the length; null when empty. */
    fun clean(input: String): String? =
        input.filterNot { it.isISOControl() }
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_LENGTH)
            .trim()
            .takeIf { it.isNotEmpty() }
}

/** Persists the identity and exposes it app-wide. */
object IdentityStore {
    private const val PREFS = "lifeline_identity"
    private const val KEY_NAME = "name"
    private const val KEY_ANONYMOUS = "anonymous"
    private const val KEY_ALIAS = "alias"
    private const val KEY_SETUP_DONE = "setup_done"

    private val _state = MutableStateFlow(IdentityState("", anonymous = true, alias = Names.anonymousAlias(), setupDone = false))
    val state: StateFlow<IdentityState> = _state.asStateFlow()

    @Volatile private var loaded = false

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = prefs(context)
        val stored = prefs.getString(KEY_ALIAS, null)
        val alias = (stored?.let(Names::migrateAlias) ?: Names.anonymousAlias()).also {
            if (it != stored) prefs.edit().putString(KEY_ALIAS, it).apply()
        }
        _state.value = IdentityState(
            name = prefs.getString(KEY_NAME, "").orEmpty(),
            anonymous = prefs.getBoolean(KEY_ANONYMOUS, false),
            alias = alias,
            setupDone = prefs.getBoolean(KEY_SETUP_DONE, false)
        )
    }

    /** First-launch choice: a name, or null to stay anonymous. */
    fun completeSetup(context: Context, name: String?) {
        val clean = name?.let(Names::clean)
        save(context, _state.value.copy(name = clean.orEmpty(), anonymous = clean == null, setupDone = true))
    }

    fun setAnonymous(context: Context, anonymous: Boolean) {
        if (!anonymous && _state.value.name.isBlank()) return
        save(context, _state.value.copy(anonymous = anonymous))
    }

    /** Changes the name and switches to it. */
    fun rename(context: Context, name: String) {
        val clean = Names.clean(name) ?: return
        save(context, _state.value.copy(name = clean, anonymous = false))
    }

    private fun save(context: Context, state: IdentityState) {
        _state.value = state
        prefs(context).edit()
            .putString(KEY_NAME, state.name)
            .putBoolean(KEY_ANONYMOUS, state.anonymous)
            .putString(KEY_ALIAS, state.alias)
            .putBoolean(KEY_SETUP_DONE, state.setupDone)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
