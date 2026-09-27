package com.lifeline.app.translate

import com.lifeline.app.assistant.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatorPromptTest {

    private val english = Languages.byTag("en-US")!!
    private val spanish = Languages.byTag("es-ES")!!
    private val nepali = Languages.byTag("ne-NP")!!

    @Test
    fun promptNamesBothLanguages() {
        val system = TranslatorPrompt.system(english, spanish)
        assertTrue(system.contains("from English to Spanish"))
        assertEquals("Translate from English to Spanish:\n\nAre you hurt?", TranslatorPrompt.user("  Are you hurt? ", english, spanish))
    }

    @Test
    fun cleanRemovesWrappers() {
        assertEquals("¿Estás herido?", TranslatorPrompt.clean("\"¿Estás herido?\""))
        assertEquals("¿Estás herido?", TranslatorPrompt.clean("Here is the translation: ¿Estás herido?"))
        assertEquals("¿Estás herido?", TranslatorPrompt.clean("Translation (Spanish): «¿Estás herido?»"))
        assertEquals("Hola", TranslatorPrompt.clean("<think>easy</think>Hola"))
    }

    @Test
    fun cleanKeepsInnerQuotes() {
        assertEquals("He said \"wait\" and left", TranslatorPrompt.clean("He said \"wait\" and left"))
    }

    @Test
    fun smallModelSupportsItsEightLanguagesOnly() {
        val lfm = ModelCatalog.byId("lfm2.5-1.2b")
        val gemma = ModelCatalog.byId("gemma4-e2b")
        assertTrue(Languages.isWellSupported(lfm, spanish))
        assertFalse(Languages.isWellSupported(lfm, nepali))
        assertTrue(Languages.isWellSupported(gemma, nepali))
        assertFalse(Languages.isWellSupported(null, spanish))
    }

    @Test
    fun languageTagsAreUnique() {
        assertEquals(Languages.all.size, Languages.all.map { it.tag }.toSet().size)
    }
}
