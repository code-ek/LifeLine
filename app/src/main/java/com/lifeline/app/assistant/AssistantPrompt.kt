package com.lifeline.app.assistant

/** Builds the model prompt, grounding it in the built-in guide so small models stay accurate. */
object AssistantPrompt {

    val system = """
        You are a helpful offline assistant on a phone with no internet connection.
        You can answer questions on any topic: survival, navigation, general knowledge, science, cooking, repairs, and more.
        You are especially strong at emergency first aid and disaster response.
        Reply with short, practical answers. Use a numbered list when steps are needed (at most 7 steps).
        Use plain language. No headings, no tables, no markdown symbols.
        If someone's life may be at risk, first tell them to get emergency help and to send an SOS from the SOS tab.
        For medical advice, only give guidance that is standard lay first aid. If you are not sure, say so.
    """.trimIndent()

    fun build(question: String, guide: AssistantReply?): String = buildString {
        append(question.trim())
        if (guide != null && guide.matched) {
            append("\n\nTrusted first-aid guidance for \"").append(guide.title).append("\" (follow it, do not contradict it):\n")
            guide.steps.dropLast(1).forEachIndexed { i, step -> append(i + 1).append(". ").append(step).append('\n') }
        }
    }
}
