package com.lifeline.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantPromptTest {

    @Test
    fun matchedQuestionIncludesGuideStepsButNotTheSosFooter() {
        val guide = FirstAidGuide.answer("someone is bleeding badly")
        val prompt = AssistantPrompt.build("someone is bleeding badly", guide)
        assertTrue(prompt.startsWith("someone is bleeding badly"))
        assertTrue(prompt.contains("Severe bleeding"))
        assertTrue(prompt.contains("1. "))
        assertFalse(prompt.contains("send an SOS from the SOS tab"))
    }

    @Test
    fun unmatchedQuestionIsSentAlone() {
        val guide = FirstAidGuide.answer("what's the weather")
        assertEquals("what's the weather", AssistantPrompt.build("  what's the weather ", guide))
    }

    @Test
    fun cleanModelTextStripsThinkingAndMarkdown() {
        val raw = "<think>internal</think>## Steps\n**1. Press** firmly\n* Keep warm"
        assertEquals("Steps\n1. Press firmly\n• Keep warm", cleanModelText(raw))
    }

    @Test
    fun cleanModelTextHidesUnfinishedThinking() {
        assertEquals("", cleanModelText("<think>still reasoning"))
    }
}
