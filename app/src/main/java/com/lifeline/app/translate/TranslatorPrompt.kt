package com.lifeline.app.translate

import com.lifeline.app.assistant.cleanModelText

/** Prompting and output clean-up for translating with a small on-device model. */
object TranslatorPrompt {

    fun system(from: Language, to: Language): String = """
        You are an interpreter helping people talk during an emergency.
        Translate each message from ${from.name} to ${to.name}.
        Reply with the ${to.name} translation only: no quotes, notes, explanations or pronunciation guides.
        Keep the meaning, tone, names and numbers exactly.
    """.trimIndent()

    fun user(text: String, from: Language, to: Language): String =
        "Translate from ${from.name} to ${to.name}:\n\n${text.trim()}"

    private val preambles = listOf(
        Regex("(?i)^here(?:'s| is) (?:the )?translation[^:\\n]*:\\s*"),
        Regex("(?i)^translation[^:\\n]*:\\s*"),
    )
    private const val QUOTES = "\"'“”„«»「」『』"

    /** Strips wrappers small models add around a translation. */
    fun clean(raw: String): String {
        var text = cleanModelText(raw)
        preambles.forEach { text = text.replace(it, "") }
        text = text.trim()
        if (text.length >= 2 && text.first() in QUOTES && text.last() in QUOTES) {
            text = text.substring(1, text.length - 1).trim()
        }
        return text
    }
}
