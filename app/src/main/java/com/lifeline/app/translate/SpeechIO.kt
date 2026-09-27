package com.lifeline.app.translate

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/** Reads translations aloud with the phone's built-in voices (offline when the voice is installed). */
object Speaker {
    private const val TAG = "Speaker"
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) Log.w(TAG, "Text-to-speech unavailable ($status)")
        }
    }

    /** Returns false if no voice exists for [language], so the UI can say so. */
    fun speak(language: Language, text: String): Boolean {
        val engine = tts ?: return false
        if (!ready || text.isBlank()) return false
        val locale = Locale.forLanguageTag(language.tag)
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return false
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translate-${text.hashCode()}")
        return true
    }

    fun stop() {
        tts?.stop()
    }
}

/**
 * Speech-to-text for one utterance. Prefers the on-device recognizer so it works without
 * internet; the language pack must be installed on the phone for that to succeed.
 */
class Listener(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean
        get() = onDeviceAvailable() || SpeechRecognizer.isRecognitionAvailable(context)

    private fun onDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start(
        language: Language,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        cancel()
        val created = when {
            onDeviceAvailable() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
            else -> {
                onError("Speech input isn't available on this phone. Type instead.")
                return
            }
        }
        recognizer = created
        created.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) onError("Didn't catch that. Try again.") else onResult(text)
                cancel()
            }

            override fun onError(error: Int) {
                onError(describe(error, language))
                cancel()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        created.startListening(intent)
    }

    /** Stops listening and delivers what was heard so far. */
    fun finish() {
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.let {
            try { it.cancel(); it.destroy() } catch (_: Exception) { }
        }
        recognizer = null
    }

    private fun describe(error: Int, language: Language): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed to listen."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "Offline speech for ${language.name} isn't installed on this phone. Type instead, or add it in " +
                "Settings › Speech recognition while online."
        else -> "Couldn't listen (error $error). Type instead."
    }
}
