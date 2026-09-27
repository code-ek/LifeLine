package com.lifeline.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class IdentityTest {

    @Test
    fun displayNameFollowsAnonymousSwitch() {
        val named = IdentityState("Alex", anonymous = false, alias = "Anon4821", setupDone = true)
        assertEquals("Alex", named.displayName)
        assertEquals("Anon4821", named.copy(anonymous = true).displayName)
    }

    @Test
    fun blankNameAlwaysShowsAlias() {
        val state = IdentityState("", anonymous = false, alias = "Anon1000", setupDone = true)
        assertEquals("Anon1000", state.displayName)
        assertEquals(setOf("Anon1000"), state.ownNames)
    }

    @Test
    fun ownNamesIncludeBothIdentities() {
        val state = IdentityState("Alex", anonymous = true, alias = "Anon4821", setupDone = true)
        assertEquals(setOf("Alex", "Anon4821"), state.ownNames)
    }

    @Test
    fun cleanTrimsCollapsesAndCaps() {
        assertEquals("Alex Smith", Names.clean("  Alex \n  Smith "))
        assertEquals(Names.MAX_LENGTH, Names.clean("x".repeat(40))!!.length)
        assertNull(Names.clean("   "))
        assertNull(Names.clean("\u0007"))
    }

    @Test
    fun randomNameIsTwoFriendlyWords() {
        val name = Names.random(Random(7))
        assertEquals(2, name.split(" ").size)
        assertTrue(name.length <= Names.MAX_LENGTH)
    }

    @Test
    fun oldLongAliasIsShortened() {
        assertEquals("Anon2783", Names.migrateAlias("Anonymous 2783"))
        assertEquals("Anon2783", Names.migrateAlias("Anon2783"))
        assertEquals("Anna", Names.migrateAlias("Anna"))
    }

    @Test
    fun onlyRealAliasesCountAsAnonymous() {
        assertTrue(Names.isAlias("Anon2783"))
        assertTrue(!Names.isAlias("Anna"))
        assertTrue(!Names.isAlias("Anonymous"))
    }

    @Test
    fun aliasHasFourDigits() {
        repeat(50) { seed ->
            assertTrue(Regex("Anon\\d{4}").matches(Names.anonymousAlias(Random(seed))))
        }
    }
}
