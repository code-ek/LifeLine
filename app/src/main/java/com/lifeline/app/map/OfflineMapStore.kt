package com.lifeline.app.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/** Free OpenStreetMap vector style (no API key). Downloaded regions cache it for offline use. */
const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

data class OfflineMapInfo(
    val id: Long,
    val name: String,
    val bounds: GeoBounds,
    val maxZoom: Int,
    val completedResources: Long,
    val requiredResources: Long,
    val bytes: Long,
    val isComplete: Boolean,
    val isDownloading: Boolean,
    val error: String? = null
) {
    val progress: Float
        get() = if (requiredResources <= 0) 0f else (completedResources.toFloat() / requiredResources).coerceIn(0f, 1f)
}

/**
 * Downloadable offline map regions, backed by MapLibre's offline database. A region holds the
 * vector tiles, fonts and icons for an area, so the map renders there with no internet.
 */
object OfflineMapStore {
    private const val TAG = "OfflineMapStore"

    private val _regions = MutableStateFlow<List<OfflineMapInfo>>(emptyList())
    val regions: StateFlow<List<OfflineMapInfo>> = _regions.asStateFlow()

    private val live = mutableMapOf<Long, OfflineRegion>()

    private fun manager(context: Context): OfflineManager {
        MapLibre.getInstance(context.applicationContext)
        return OfflineManager.getInstance(context.applicationContext)
    }

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Loads saved regions and resumes any unfinished downloads. */
    fun refresh(context: Context) {
        manager(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions.orEmpty()
                regions.forEach { region ->
                    live[region.id] = region
                    if (_regions.value.none { it.id == region.id }) upsert(placeholder(region))
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            status ?: return
                            upsert(infoFor(region, status, downloading = false))
                            if (!status.isComplete && isOnline(context)) startDownload(region)
                        }
                        override fun onError(error: String?) {
                            Log.w(TAG, "Status failed for ${region.id}: $error")
                        }
                    })
                }
                val ids = regions.map { it.id }.toSet()
                _regions.update { list -> list.filter { it.id in ids } }
            }

            override fun onError(error: String) {
                Log.w(TAG, "Listing offline regions failed: $error")
            }
        })
    }

    fun download(context: Context, name: String, bounds: GeoBounds, maxZoom: Int, onError: (String) -> Unit = {}) {
        val definition = OfflineTilePyramidRegionDefinition(
            MAP_STYLE_URL,
            LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west),
            0.0,
            maxZoom.toDouble(),
            context.resources.displayMetrics.density
        )
        val metadata = JSONObject().put("name", name).put("maxZoom", maxZoom).toString().toByteArray()
        manager(context).createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                live[offlineRegion.id] = offlineRegion
                upsert(placeholder(offlineRegion).copy(isDownloading = true))
                startDownload(offlineRegion)
            }

            override fun onError(error: String) {
                Log.w(TAG, "Creating region failed: $error")
                onError(error)
            }
        })
    }

    fun delete(context: Context, id: Long) {
        val region = live[id] ?: return
        region.setObserver(null)
        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                live.remove(id)
                _regions.update { list -> list.filterNot { it.id == id } }
            }

            override fun onError(error: String) {
                Log.w(TAG, "Deleting region $id failed: $error")
            }
        })
    }

    /** True if a fully downloaded region covers this point. */
    fun covers(lat: Double, lon: Double): Boolean =
        _regions.value.any { it.isComplete && it.bounds.contains(lat, lon) }

    private fun startDownload(region: OfflineRegion) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                upsert(infoFor(region, status, downloading = !status.isComplete))
                if (status.isComplete) region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            }

            override fun onError(error: OfflineRegionError) {
                Log.w(TAG, "Region ${region.id} error: ${error.reason} ${error.message}")
                _regions.update { list ->
                    list.map { if (it.id == region.id) it.copy(error = error.message) else it }
                }
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                Log.w(TAG, "Tile limit $limit exceeded for ${region.id}")
            }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }

    private fun placeholder(region: OfflineRegion): OfflineMapInfo {
        val definition = region.definition as? OfflineTilePyramidRegionDefinition
        val b = definition?.bounds
        val meta = runCatching { JSONObject(String(region.metadata)) }.getOrNull()
        return OfflineMapInfo(
            id = region.id,
            name = meta?.optString("name")?.takeIf { it.isNotBlank() } ?: "Map area ${region.id}",
            bounds = if (b != null) GeoBounds(b.getLatNorth(), b.getLatSouth(), b.getLonEast(), b.getLonWest())
            else GeoBounds(0.0, 0.0, 0.0, 0.0),
            maxZoom = meta?.optInt("maxZoom") ?: definition?.maxZoom?.toInt() ?: 0,
            completedResources = 0,
            requiredResources = 0,
            bytes = 0,
            isComplete = false,
            isDownloading = false
        )
    }

    private fun infoFor(region: OfflineRegion, status: OfflineRegionStatus, downloading: Boolean): OfflineMapInfo {
        val base = _regions.value.firstOrNull { it.id == region.id } ?: placeholder(region)
        return base.copy(
            completedResources = status.completedResourceCount,
            requiredResources = status.requiredResourceCount,
            bytes = status.completedResourceSize,
            isComplete = status.isComplete,
            isDownloading = downloading,
            error = null
        )
    }

    private fun upsert(info: OfflineMapInfo) {
        _regions.update { list ->
            if (list.any { it.id == info.id }) list.map { if (it.id == info.id) info else it } else list + info
        }
    }
}
