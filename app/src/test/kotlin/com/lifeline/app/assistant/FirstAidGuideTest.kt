package com.lifeline.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstAidGuideTest {

    @Test
    fun answersTheHackathonDemoQuestions() {
        assertEquals("Unconscious person", FirstAidGuide.answer("Someone is unconscious. What should I do?").title)
        assertEquals("Fire", FirstAidGuide.answer("There is a fire nearby. What should I do?").title)
        assertEquals("Severe bleeding", FirstAidGuide.answer("How do I treat severe bleeding while waiting for help?").title)
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals("Choking", FirstAidGuide.answer("MY FRIEND IS CHOKING").title)
    }

    @Test
    fun keywordsMatchWholeWordStartsOnly() {
        // "minutes" must not trigger the allergy topic via "nut".
        assertFalse(FirstAidGuide.answer("wait five minutes").matched)
        assertEquals("Burns", FirstAidGuide.answer("my hand got burned").title)
    }

    @Test
    fun matchedRepliesPointToSos() {
        val reply = FirstAidGuide.answer("someone is bleeding")
        assertTrue(reply.matched)
        assertTrue(reply.steps.last().contains("SOS"))
    }

    @Test
    fun unknownQuestionListsTopics() {
        val reply = FirstAidGuide.answer("what's the weather")
        assertFalse(reply.matched)
        assertTrue(reply.steps.first().contains("Burns"))
    }

    @Test
    fun everySuggestionChipGetsARealAnswer() {
        FirstAidGuide.suggestions.forEach { assertTrue(it, FirstAidGuide.answer(it).matched) }
    }
}
