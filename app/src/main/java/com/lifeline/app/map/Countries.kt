package com.lifeline.app.map

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class Country(val name: String, val code: String, val bounds: GeoBounds)

/** Country list for "download a country", from the bundled Natural Earth derived assets/countries.json. */
object Countries {
    private const val ASSET = "countries.json"

    private data class Row(
        @SerializedName("name") val name: String?,
        @SerializedName("code") val code: String?,
        @SerializedName("w") val w: Double?,
        @SerializedName("s") val s: Double?,
        @SerializedName("e") val e: Double?,
        @SerializedName("n") val n: Double?
    )

    @Volatile
    private var cached: List<Country>? = null

    fun all(context: Context): List<Country> =
        cached ?: context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }.also { cached = it }

    fun parse(json: String): List<Country> =
        Gson().fromJson(json, Array<Row>::class.java).orEmpty().mapNotNull { row ->
            val name = row.name ?: return@mapNotNull null
            val n = row.n ?: return@mapNotNull null
            val s = row.s ?: return@mapNotNull null
            val e = row.e ?: return@mapNotNull null
            val w = row.w ?: return@mapNotNull null
            if (n < s) return@mapNotNull null
            Country(name, row.code.orEmpty(), GeoBounds(north = n, south = s, east = e, west = w))
        }

    fun search(countries: List<Country>, query: String): List<Country> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return countries
        return countries
            .filter { it.name.lowercase().contains(q) || it.code.lowercase() == q }
            .sortedBy { !it.name.lowercase().startsWith(q) }
    }
}
