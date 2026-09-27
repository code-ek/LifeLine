package com.lifeline.app.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Geographic bounding box in degrees. */
data class GeoBounds(val north: Double, val south: Double, val east: Double, val west: Double) {
    init {
        require(north >= south) { "north must be >= south" }
    }

    fun contains(lat: Double, lon: Double): Boolean =
        lat in south..north && lon in west..east
}

/** Web-mercator tile arithmetic used to size offline downloads before starting them. */
object TileMath {
    /** Highest detail the OpenFreeMap vector tiles provide; MapLibre over-zooms beyond it. */
    const val MAX_SOURCE_ZOOM = 14

    /** Rough average vector tile size, used only for the "about N MB" estimate. */
    private const val AVG_TILE_BYTES = 25_000L

    /** Fonts, icons and style files downloaded once per region (measured ~10 MB). */
    private const val STYLE_OVERHEAD_BYTES = 10_000_000L

    private const val MAX_LAT = 85.05112878

    fun tileX(lon: Double, zoom: Int): Int {
        val n = 1 shl zoom
        return floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    fun tileY(lat: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val rad = lat.coerceIn(-MAX_LAT, MAX_LAT) * PI / 180.0
        val y = (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * n
        return floor(y).toInt().coerceIn(0, n - 1)
    }

    fun tileCount(bounds: GeoBounds, zoom: Int): Long {
        val xs = (tileX(bounds.east, zoom) - tileX(bounds.west, zoom) + 1).toLong()
        val ys = (tileY(bounds.south, zoom) - tileY(bounds.north, zoom) + 1).toLong()
        return xs * ys
    }

    fun tileCount(bounds: GeoBounds, minZoom: Int, maxZoom: Int): Long =
        (minZoom..maxZoom).sumOf { tileCount(bounds, it) }

    fun estimatedBytes(bounds: GeoBounds, minZoom: Int, maxZoom: Int): Long =
        tileCount(bounds, minZoom, maxZoom.coerceAtMost(MAX_SOURCE_ZOOM)) * AVG_TILE_BYTES + STYLE_OVERHEAD_BYTES

    /** Most detailed zoom (up to street level) whose download stays within [tileBudget]. */
    fun maxZoomWithin(bounds: GeoBounds, tileBudget: Long, minZoom: Int = 0, floorZoom: Int = 5): Int {
        var best = floorZoom
        for (z in floorZoom..MAX_SOURCE_ZOOM) {
            if (tileCount(bounds, minZoom, z) <= tileBudget) best = z else break
        }
        return best
    }

    fun detailLabel(maxZoom: Int): String = when {
        maxZoom >= 14 -> "street detail"
        maxZoom >= 12 -> "town detail"
        maxZoom >= 9 -> "city and road detail"
        else -> "overview"
    }
}
