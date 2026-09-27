package com.lifeline.app.assistant

/** Builds the model prompt, grounding it in the built-in guide so small models stay accurate. */
object AssistantPrompt {

    val system = """
        You are an offline emergency first-aid assistant on a phone with no internet.
        The person asking is a bystander who needs to act right now.
        Reply with short, calm, practical steps as a numbered list of at most 7 steps.
        Use plain language. No headings, no tables, no markdown symbols.
        If someone's life may be at risk, first tell them to get emergency help and to send an SOS from the SOS tab.
        Only give medication advice that is standard lay first aid. If you are not sure, say so.
    """.trimIndent()

    fun build(question: String, guide: AssistantReply?): String = buildString {
        append(question.trim())
        if (guide != null && guide.matched) {
            append("\n\nTrusted first-aid guidance for \"").append(guide.title).append("\" (follow it, do not contradict it):\n")
            guide.steps.dropLast(1).forEachIndexed { i, step -> append(i + 1).append(". ").append(step).append('\n') }
        }
    }
}
