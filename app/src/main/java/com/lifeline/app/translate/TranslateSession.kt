package com.lifeline.app.translate

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.lifeline.app.assistant.ModelManager
import com.lifeline.app.assistant.OnDeviceLlm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Who is talking: this phone's owner or the person they are helping. */
enum class Party { ME, THEM }

data class TranslationItem(
    val id: Long,
    val speaker: Party,
    val from: Language,
    val to: Language,
    val original: String,
    val translated: String,
    val done: Boolean,
    val error: String? = null
)

/** Face-to-face translation between "me" and "them", run by the on-device model. */
object TranslateSession {
    private const val TAG = "TranslateSession"
    private const val PREFS = "translate"

    val items = mutableStateListOf<TranslationItem>()

    private val _mine = MutableStateFlow(Languages.all.first())
    val mine: StateFlow<Language> = _mine.asStateFlow()
    private val _theirs = MutableStateFlow(Languages.byTag("es-ES")!!)
    val theirs: StateFlow<Language> = _theirs.asStateFlow()

    /** Speak each translation aloud as soon as it is ready. */
    private val _autoSpeak = MutableStateFlow(true)
    val autoSpeak: StateFlow<Boolean> = _autoSpeak.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var nextId = 0L
    @Volatile private var loaded = false

    val quickPhrases = listOf(
        "Are you hurt?",
        "Where does it hurt?",
        "I'm here to help you.",
        "Can you walk?",
        "Is anyone else with you?",
        "Do you need water or medicine?",
        "Stay calm. Help is coming.",
        "Follow me to safety."
    )

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        Languages.byTag(prefs.getString("mine", null))?.let { _mine.value = it }
        Languages.byTag(prefs.getString("theirs", null))?.let { _theirs.value = it }
        _autoSpeak.value = prefs.getBoolean("autoSpeak", true)
        Speaker.init(context)
    }

    fun setLanguages(context: Context, mine: Language, theirs: Language) {
        _mine.value = mine
        _theirs.value = theirs
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("mine", mine.tag).putString("theirs", theirs.tag).apply()
    }

    fun swap(context: Context) = setLanguages(context, _theirs.value, _mine.value)

    fun setAutoSpeak(context: Context, on: Boolean) {
        _autoSpeak.value = on
        if (!on) Speaker.stop()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("autoSpeak", on).apply()
    }

    /** Translates what [who] said into the other person's language. */
    fun translate(context: Context, who: Party, text: String, fromOverride: Language? = null) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val from = fromOverride ?: if (who == Party.ME) _mine.value else _theirs.value
        val to = if (who == Party.ME) _theirs.value else _mine.value
        val id = nextId++
        items.add(TranslationItem(id, who, from, to, clean, "", done = false))

        val model = ModelManager.activeModel(context)
        if (model == null) {
            update(id) { it.copy(done = true, error = "Download the offline AI model to translate.") }
            return
        }
        val app = context.applicationContext
        scope.launch {
            try {
                if (!OnDeviceLlm.isLoaded) _status.value = "Loading AI model…"
                OnDeviceLlm.load(app, model)
                _status.value = "Translating…"
                val out = StringBuilder()
                OnDeviceLlm.stream(
                    model,
                    TranslatorPrompt.system(from, to),
                    TranslatorPrompt.user(clean, from, to),
                    temperature = 0.1,
                    maxTokens = 256
                ).collect { chunk ->
                    out.append(chunk)
                    update(id) { it.copy(translated = TranslatorPrompt.clean(out.toString())) }
                }
                val result = TranslatorPrompt.clean(out.toString())
                update(id) { it.copy(translated = result, done = true, error = if (result.isBlank()) "No translation came back. Try again." else null) }
                if (result.isNotBlank() && _autoSpeak.value && !Speaker.speak(to, result)) {
                    Speaker.installVoiceIfNeeded(app, to)
                    update(id) { it.copy(error = "Installing ${to.name} voice — next translation will read aloud.") }
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Log.e(TAG, "Translation failed", t)
                update(id) { it.copy(done = true, error = "Translation failed on this phone.") }
            } finally {
                _status.value = null
            }
        }
    }

    fun clear() {
        items.clear()
        Speaker.stop()
    }

    private fun update(id: Long, change: (TranslationItem) -> TranslationItem) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) items[index] = change(items[index])
    }
}
