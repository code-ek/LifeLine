package com.lifeline.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

/** Keeps one download to a few hundred MB so it finishes on a phone connection. */
private const val TILE_BUDGET = 12_000L
private const val OVERVIEW_MAX_ZOOM = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapsSheet(controller: MapController, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val regions by OfflineMapStore.regions.collectAsStateWithLifecycle()
    val online = remember { OfflineMapStore.isOnline(context) }
    val countries = remember { runCatching { Countries.all(context) }.getOrDefault(emptyList()) }
    val visible = remember { controller.visibleBounds() }
    var streets by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Offline maps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Download maps while you have internet. They keep working with no signal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!online) {
                item {
                    Notice("You're offline. Downloading needs internet; maps already on this phone still work.")
                }
            }
            error?.let { item { Notice("Download failed: $it") } }

            item { SectionTitle("Download this area") }
            item {
                AreaDownload(
                    visible = visible,
                    streets = streets,
                    onStreetsChange = { streets = it },
                    enabled = online,
                    countryName = visible?.let { b -> countryAt(countries, (b.north + b.south) / 2, (b.east + b.west) / 2) }
                ) { bounds, maxZoom, name ->
                    OfflineMapStore.download(context, name, bounds, maxZoom) { error = it }
                }
            }

            item { SectionTitle("Download a country") }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Search country, e.g. Nepal") }
                )
            }
            if (query.isNotBlank()) {
                val matches = Countries.search(countries, query).take(6)
                if (matches.isEmpty()) item { Text("No country matches \"$query\".") }
                items(matches, key = { "c-${it.code}-${it.name}" }) { country ->
                    CountryRow(country, enabled = online) {
                        val maxZoom = TileMath.maxZoomWithin(country.bounds, TILE_BUDGET)
                        OfflineMapStore.download(context, country.name, country.bounds, maxZoom) { error = it }
                        query = ""
                    }
                }
            }

            item { SectionTitle("On this phone") }
            if (regions.isEmpty()) {
                item { Text("No offline maps yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(regions, key = { it.id }) { region ->
                RegionRow(
                    region,
                    onShow = { controller.showBounds(region.bounds); onDismiss() },
                    onDelete = { OfflineMapStore.delete(context, region.id) }
                )
            }
        }
    }
}

@Composable
private fun AreaDownload(
    visible: GeoBounds?,
    streets: Boolean,
    onStreetsChange: (Boolean) -> Unit,
    enabled: Boolean,
    countryName: String?,
    onDownload: (GeoBounds, Int, String) -> Unit
) {
    if (visible == null) {
        Text("Open the map first, then come back here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val maxZoom = if (streets) TileMath.MAX_SOURCE_ZOOM else OVERVIEW_MAX_ZOOM
    val tiles = TileMath.tileCount(visible, 0, maxZoom)
    val tooBig = tiles > TILE_BUDGET
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Saves what's on screen now. Move and zoom the map to choose the area.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = streets, onClick = { onStreetsChange(true) }, label = { Text("Streets") })
            FilterChip(selected = !streets, onClick = { onStreetsChange(false) }, label = { Text("Overview") })
        }
        Text(
            if (tooBig) "Too large for this detail. Zoom the map in, or choose Overview."
            else "About ${formatMb(TileMath.estimatedBytes(visible, 0, maxZoom))} · ${TileMath.detailLabel(maxZoom)}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (tooBig) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Button(
            onClick = { onDownload(visible, maxZoom, if (countryName != null) "Area in $countryName" else "Saved area") },
            enabled = enabled && !tooBig
        ) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Text("  Download this area")
        }
    }
}

@Composable
private fun CountryRow(country: Country, enabled: Boolean, onDownload: () -> Unit) {
    val maxZoom = remember(country) { TileMath.maxZoomWithin(country.bounds, TILE_BUDGET) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(country.name, fontWeight = FontWeight.SemiBold)
            Text(
                "About ${formatMb(TileMath.estimatedBytes(country.bounds, 0, maxZoom))} · ${TileMath.detailLabel(maxZoom)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onDownload, enabled = enabled) { Text("Download") }
    }
}

@Composable
private fun RegionRow(region: OfflineMapInfo, onShow: () -> Unit, onDelete: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (region.isComplete) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Ready", tint = Color(0xFF34C759))
            }
            Column(Modifier.weight(1f).padding(start = if (region.isComplete) 10.dp else 0.dp)) {
                Text(region.name, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        region.error != null -> "Paused: ${region.error}"
                        region.isComplete -> "${formatMb(region.bytes)} · ready offline · ${TileMath.detailLabel(region.maxZoom)}"
                        else -> "Downloading ${(region.progress * 100).toInt()}% · ${formatMb(region.bytes)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onShow) { Text("Show") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${region.name}") }
        }
        if (!region.isComplete) {
            LinearProgressIndicator(progress = { region.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(bottom = 12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
        color = MaterialTheme.colorScheme.onErrorContainer
    )
}

private fun countryAt(countries: List<Country>, lat: Double, lon: Double): String? =
    countries.filter { it.bounds.contains(lat, lon) }
        .minByOrNull { (it.bounds.north - it.bounds.south) * (it.bounds.east - it.bounds.west) }
        ?.name

internal fun formatMb(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb < 10) String.format(Locale.US, "%.1f MB", mb) else String.format(Locale.US, "%.0f MB", mb)
}
