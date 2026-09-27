package com.lifeline.app.assistant

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Runs a downloaded model fully on the phone with LiteRT-LM (CPU, for reliability on any device). */
object OnDeviceLlm {
    private const val TAG = "OnDeviceLlm"

    private val mutex = Mutex()
    private val generation = Mutex()
    private var engine: Engine? = null
    private var loadedPath: String? = null

    val isLoaded: Boolean get() = engine != null

    suspend fun load(context: Context, model: AiModel) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val path = ModelManager.file(context, model).path
            if (engine != null && loadedPath == path) return@withLock
            closeEngine()
            val started = System.currentTimeMillis()
            val created = Engine(
                EngineConfig(
                    modelPath = path,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.path
                )
            )
            created.initialize()
            engine = created
            loadedPath = path
            Log.i(TAG, "Loaded ${model.id} in ${System.currentTimeMillis() - started} ms")
        }
    }

    /**
     * Streams the answer as it is generated, one text chunk at a time. Requests from the
     * assistant and the translator share one engine, so they run one after another.
     */
    fun stream(
        model: AiModel,
        systemPrompt: String,
        prompt: String,
        temperature: Double = 0.3,
        maxTokens: Int = 512
    ): Flow<String> = flow {
        generation.withLock {
            val current = engine ?: error("Model not loaded")
            val config = ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                samplerConfig = SamplerConfig(40, 0.9, temperature, 0),
                maxOutputToken = maxTokens,
                thinkingConfig = if (model.canThink) ThinkingConfig(false, 0) else null
            )
            val conversation = current.createConversation(config)
            try {
                conversation.sendMessageAsync(prompt).collect { emit(it.toString()) }
            } finally {
                conversation.close()
            }
        }
    }.flowOn(Dispatchers.IO)

    fun unload() {
        closeEngine()
    }

    private fun closeEngine() {
        try { engine?.close() } catch (t: Throwable) { Log.w(TAG, "Close failed: ${t.message}") }
        engine = null
        loadedPath = null
    }
}
