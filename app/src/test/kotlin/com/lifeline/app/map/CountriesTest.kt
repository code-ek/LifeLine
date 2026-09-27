package com.lifeline.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CountriesTest {

    private val sample = """
        [{"name":"Examplestan","code":"EX","w":1.0,"s":2.0,"e":3.0,"n":4.0},
         {"name":"Broken","code":"BR","w":1.0,"s":5.0,"e":3.0,"n":4.0},
         {"name":"Other Land","code":"OL","w":-3.0,"s":-4.0,"e":-1.0,"n":-2.0}]
    """.trimIndent()

    @Test
    fun parsesAndDropsInvalidRows() {
        val countries = Countries.parse(sample)
        assertEquals(listOf("Examplestan", "Other Land"), countries.map { it.name })
        assertEquals(GeoBounds(north = 4.0, south = 2.0, east = 3.0, west = 1.0), countries[0].bounds)
    }

    @Test
    fun searchMatchesNameOrCodePrefixFirst() {
        val countries = Countries.parse(sample)
        assertEquals("Other Land", Countries.search(countries, "land").first().name)
        assertEquals("Examplestan", Countries.search(countries, "ex").single().name)
        assertEquals(2, Countries.search(countries, "").size)
    }

    @Test
    fun bundledAssetParses() {
        val asset = File("src/main/assets/countries.json")
        if (!asset.exists()) return // running from another working directory
        val countries = Countries.parse(asset.readText())
        assertTrue(countries.size > 200)
        assertTrue(countries.any { it.code == "IN" })
    }
}
