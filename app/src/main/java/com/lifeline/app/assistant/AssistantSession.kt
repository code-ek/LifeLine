package com.lifeline.app.assistant

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ChatItem {
    data class Question(val text: String) : ChatItem
    data class GuideAnswer(val reply: AssistantReply, val note: String? = null) : ChatItem
    data class AiAnswer(val text: String, val modelName: String, val done: Boolean) : ChatItem
}

/**
 * The assistant conversation. Lives at process level so an answer keeps generating while the
 * user switches tabs. Uses the downloaded on-device model when there is one, otherwise (or if
 * the model fails) the built-in [FirstAidGuide].
 */
object AssistantSession {
    private const val TAG = "AssistantSession"

    val items = mutableStateListOf<ChatItem>()

    /** Non-null while working: what to show the user ("Loading AI model…", "Thinking…"). */
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    fun ask(context: Context, question: String) {
        val q = question.trim()
        if (q.isEmpty() || _status.value != null) return
        items.add(ChatItem.Question(q))
        val guide = FirstAidGuide.answer(q)
        val model = ModelManager.activeModel(context)
        if (model == null) {
            items.add(ChatItem.GuideAnswer(guide))
            return
        }

        val app = context.applicationContext
        job = scope.launch {
            val index = items.size
            items.add(ChatItem.AiAnswer("", model.name, done = false))
            try {
                if (!OnDeviceLlm.isLoaded) _status.value = "Loading AI model… first time takes a few seconds"
                OnDeviceLlm.load(app, model)
                _status.value = "Thinking…"
                val text = StringBuilder()
                OnDeviceLlm.stream(model, AssistantPrompt.system, AssistantPrompt.build(q, guide)).collect { chunk ->
                    text.append(chunk)
                    items[index] = ChatItem.AiAnswer(cleanModelText(text.toString()), model.name, done = false)
                }
                val answer = cleanModelText(text.toString())
                items[index] = if (answer.isBlank()) {
                    ChatItem.GuideAnswer(guide, note = "The AI model gave no answer, so here is the built-in guide.")
                } else {
                    ChatItem.AiAnswer(answer, model.name, done = true)
                }
            } catch (e: CancellationException) {
                (items.getOrNull(index) as? ChatItem.AiAnswer)?.let { items[index] = it.copy(done = true) }
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "On-device model failed", t)
                items[index] = ChatItem.GuideAnswer(
                    guide,
                    note = "The AI model couldn't run on this phone, so here is the built-in guide."
                )
            } finally {
                _status.value = null
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}

/** Removes reasoning blocks and markdown symbols small models sometimes emit anyway. */
internal fun cleanModelText(raw: String): String =
    raw.replace(Regex("(?s)<think>.*?(</think>|$)"), "")
        .replace("**", "")
        .replace(Regex("(?m)^#{1,6}\\s*"), "")
        .replace(Regex("(?m)^\\s*[*•-]\\s+"), "• ")
        .trim()
