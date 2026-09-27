package com.lifeline.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileMathTest {

    private val world = GeoBounds(north = 85.0, south = -85.0, east = 179.99, west = -180.0)

    @Test
    fun wholeWorldHasFourToThePowerZoomTiles() {
        assertEquals(1L, TileMath.tileCount(world, 0))
        assertEquals(4L, TileMath.tileCount(world, 1))
        assertEquals(16L, TileMath.tileCount(world, 2))
        assertEquals(21L, TileMath.tileCount(world, 0, 2))
    }

    @Test
    fun tinyAreaIsOneTilePerZoomAtLowZoom() {
        val block = GeoBounds(north = 1.2350, south = 1.2340, east = 2.3460, west = 2.3450)
        assertEquals(1L, TileMath.tileCount(block, 5))
        assertEquals(1L, TileMath.tileCount(block, 10))
    }

    @Test
    fun tileYIsNorthToSouth() {
        assertTrue(TileMath.tileY(60.0, 8) < TileMath.tileY(-60.0, 8))
        assertEquals(0, TileMath.tileY(89.9, 3))
        assertEquals(7, TileMath.tileY(-89.9, 3))
    }

    @Test
    fun maxZoomWithinRespectsBudget() {
        val germanyLike = GeoBounds(north = 55.0, south = 47.3, east = 15.0, west = 5.9)
        val z = TileMath.maxZoomWithin(germanyLike, tileBudget = 25_000)
        assertTrue("zoom $z", z in 8..13)
        assertTrue(TileMath.tileCount(germanyLike, 0, z) <= 25_000)
        assertTrue(z == TileMath.MAX_SOURCE_ZOOM || TileMath.tileCount(germanyLike, 0, z + 1) > 25_000)
    }

    @Test
    fun smallCityGetsStreetDetail() {
        val city = GeoBounds(north = 1.30, south = 1.20, east = 2.40, west = 2.30)
        assertEquals(TileMath.MAX_SOURCE_ZOOM, TileMath.maxZoomWithin(city, tileBudget = 25_000))
    }

    @Test
    fun estimateGrowsWithDetail() {
        val city = GeoBounds(north = 1.30, south = 1.20, east = 2.40, west = 2.30)
        assertTrue(TileMath.estimatedBytes(city, 0, 14) > TileMath.estimatedBytes(city, 0, 10))
    }

    @Test
    fun boundsContainment() {
        val b = GeoBounds(north = 2.0, south = 1.0, east = 3.0, west = 2.0)
        assertTrue(b.contains(1.5, 2.5))
        assertTrue(!b.contains(0.5, 2.5))
    }
}
