package com.lifeline.app.map

import android.Manifest
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeline.app.emergency.EmergencyLocationSource
import com.lifeline.app.emergency.EmergencyRuntime
import com.lifeline.app.emergency.SosAlert
import com.lifeline.app.home.ScreenHeader
import com.lifeline.app.home.SosRed
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin


/** A located SOS relative to this phone. */
private data class Blip(val alert: SosAlert, val distanceM: Float, val bearingDeg: Float)

private enum class MapMode { MAP, RADAR }

/**
 * Map tab: a MapLibre street map that works offline from downloaded regions, plus a radar view
 * (this phone at the centre, SOS alerts by distance and bearing) that needs no map data at all.
 */
@Composable
fun NearbyMapScreen() {
    val context = LocalContext.current
    val locationSource = remember { EmergencyLocationSource(context) }
    val alerts by EmergencyRuntime.alerts.collectAsStateWithLifecycle()
    val regions by OfflineMapStore.regions.collectAsStateWithLifecycle()
    var hasPermission by remember { mutableStateOf(locationSource.hasPermission()) }
    var myLocation by remember { mutableStateOf<Location?>(null) }
    var mode by rememberSaveable { mutableStateOf(MapMode.MAP) }
    var showDownloads by remember { mutableStateOf(false) }
    var styleFailed by remember { mutableStateOf(false) }
    var mapReady by remember { mutableStateOf(false) }
    var centered by remember { mutableStateOf(false) }
    var centeredAt by remember { mutableStateOf<Location?>(null) }
    val controller = remember { MapController() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasPermission = locationSource.hasPermission() }
    val requestPermission = {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(Unit) { OfflineMapStore.refresh(context) }

    DisposableEffect(hasPermission) {
        if (hasPermission) locationSource.start { myLocation = it }
        onDispose { locationSource.stop() }
    }

    val me = myLocation
    // First view: centre on this phone, otherwise on a downloaded map.
    LaunchedEffect(mapReady, me, regions) {
        if (!mapReady) return@LaunchedEffect
        // The first fix can be a stale cached one; follow a big jump (> 5 km) to the real position.
        if (me != null && (centeredAt == null || centeredAt!!.distanceTo(me) > 5_000f)) {
            controller.centerOn(me.latitude, me.longitude, 14.0); centered = true; centeredAt = me
        } else if (!centered) regions.firstOrNull { it.isComplete }?.let {
            controller.showBounds(it.bounds); centered = true
        }
    }

    val ready = regions.count { it.isComplete }
    val downloading = regions.count { !it.isComplete }
    val subtitle = when {
        downloading > 0 -> "Downloading $downloading offline map${if (downloading > 1) "s" else ""}…"
        me != null && OfflineMapStore.covers(me.latitude, me.longitude) -> "Offline map ready for your area"
        ready > 0 -> "$ready offline map${if (ready > 1) "s" else ""} on this phone"
        else -> "No offline maps yet · download one while online"
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Map", subtitle = subtitle) {
            FilledTonalButton(onClick = { showDownloads = true }) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Offline maps")
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == MapMode.MAP, onClick = { mode = MapMode.MAP }, label = { Text("Map") })
            FilterChip(selected = mode == MapMode.RADAR, onClick = { mode = MapMode.RADAR }, label = { Text("Radar") })
        }

        when (mode) {
            MapMode.MAP -> Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                OfflineMapView(
                    modifier = Modifier.fillMaxSize(),
                    controller = controller,
                    alerts = alerts,
                    myLocation = me,
                    onStyleFailed = { styleFailed = true },
                    onStyleLoaded = { styleFailed = false; mapReady = true }
                )
                if (styleFailed) {
                    Box(Modifier.align(Alignment.Center).padding(24.dp)) {
                        MessageCard(
                            text = "No map available offline yet. Connect to the internet once and tap " +
                                "Offline maps to download your area or country. The Radar view works without maps.",
                            action = "Use radar"
                        ) { mode = MapMode.RADAR }
                    }
                }
                if (me != null) {
                    SmallFloatingActionButton(
                        onClick = { controller.centerOn(me.latitude, me.longitude, 15.0) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                    ) { Icon(Icons.Filled.MyLocation, contentDescription = "Centre on me") }
                } else if (!hasPermission) {
                    SmallFloatingActionButton(
                        onClick = requestPermission,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                    ) { Icon(Icons.Filled.LocationSearching, contentDescription = "Allow location") }
                }
            }
            MapMode.RADAR -> RadarPanel(
                modifier = Modifier.weight(1f),
                alerts = alerts,
                me = me,
                hasPermission = hasPermission,
                gpsEnabled = locationSource.isGpsEnabled(),
                onRequestPermission = requestPermission
            )
        }
    }

    if (showDownloads) {
        OfflineMapsSheet(controller = controller, onDismiss = { showDownloads = false })
    }
}

@Composable
private fun RadarPanel(
    modifier: Modifier,
    alerts: List<SosAlert>,
    me: Location?,
    hasPermission: Boolean,
    gpsEnabled: Boolean,
    onRequestPermission: () -> Unit
) {
    val blips = if (me == null) emptyList() else alerts.mapNotNull { alert ->
        val loc = alert.payload.location ?: return@mapNotNull null
        if (alert.isLocal) return@mapNotNull null
        val result = FloatArray(2)
        Location.distanceBetween(me.latitude, me.longitude, loc.latitude, loc.longitude, result)
        Blip(alert, result[0], (result[1] + 360f) % 360f)
    }.sortedBy { it.distanceM }
    val unlocated = alerts.count { !it.isLocal && it.payload.location == null }

    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                when {
                    !hasPermission -> MessageCard(
                        text = "Allow location to see SOS alerts around you. GPS works without internet.",
                        action = "Allow location",
                        onAction = onRequestPermission
                    )
                    me == null -> MessageCard(
                        text = if (gpsEnabled) "Getting GPS fix… faster outdoors with a view of the sky."
                        else "GPS is off. Turn on Location in system settings."
                    )
                    else -> Radar(blips)
                }
            }
        }
        if (me != null) {
            item {
                Text(
                    "You · ${String.format(Locale.US, "%.5f, %.5f", me.latitude, me.longitude)}" +
                        if (me.hasAccuracy()) " (±${me.accuracy.toInt()} m)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        if (blips.isEmpty()) {
            item {
                Text(
                    if (unlocated > 0) "$unlocated SOS alert(s) nearby without a shared location. See the SOS tab."
                    else "No SOS alerts with a location yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            items(blips, key = { it.alert.payload.id }) { blip -> BlipRow(blip) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun Radar(blips: List<Blip>) {
    val ringColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val meColor = MaterialTheme.colorScheme.secondary
    val meRingColor = MaterialTheme.colorScheme.onSecondary
    val measurer = rememberTextMeasurer()
    val range = niceRange(blips.maxOfOrNull { it.distanceM } ?: 0f)

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(background, RoundedCornerShape(20.dp))
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 * 0.86f

        for (i in 1..4) {
            drawCircle(ringColor, radius * i / 4, center, style = Stroke(width = 1.dp.toPx()))
        }
        drawLine(ringColor, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius))
        drawLine(ringColor, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y))

        drawText(
            measurer, "N", Offset(center.x - 5.dp.toPx(), center.y - radius - 18.dp.toPx()),
            style = TextStyle(color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        )
        drawText(
            measurer, formatDistance(range), Offset(center.x + 4.dp.toPx(), center.y - radius + 2.dp.toPx()),
            style = TextStyle(color = labelColor, fontSize = 10.sp)
        )

        blips.forEach { blip ->
            val r = radius * (blip.distanceM / range).coerceAtMost(1f)
            val angle = Math.toRadians(blip.bearingDeg.toDouble())
            val p = Offset(center.x + (r * sin(angle)).toFloat(), center.y - (r * cos(angle)).toFloat())
            drawCircle(SosRed.copy(alpha = 0.25f), 14.dp.toPx(), p)
            drawCircle(SosRed, 7.dp.toPx(), p)
            drawText(
                measurer, blip.alert.payload.type.label, Offset(p.x + 10.dp.toPx(), p.y - 8.dp.toPx()),
                style = TextStyle(color = SosRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
        }

        drawCircle(meColor.copy(alpha = 0.25f), 16.dp.toPx(), center)
        drawCircle(meRingColor, 8.dp.toPx(), center)
        drawCircle(meColor, 6.dp.toPx(), center)
    }
}

@Composable
private fun BlipRow(blip: Blip) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = SosRed.copy(alpha = 0.10f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(SosRed, CircleShape))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    "${blip.alert.payload.type.label} · ${blip.alert.senderNickname ?: "unknown"}",
                    fontWeight = FontWeight.SemiBold
                )
                if (blip.alert.payload.description.isNotBlank()) {
                    Text(blip.alert.payload.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
            Text(
                "${formatDistance(blip.distanceM)} ${compass(blip.bearingDeg)}",
                fontWeight = FontWeight.Bold,
                color = SosRed
            )
        }
    }
}

@Composable
private fun MessageCard(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (action != null) Button(onClick = onAction) { Text(action) }
        }
    }
}

/** Radar range: the farthest blip rounded up to a readable distance, at least 100 m. */
internal fun niceRange(maxDistanceM: Float): Float {
    val steps = floatArrayOf(100f, 250f, 500f, 1_000f, 2_000f, 5_000f, 10_000f, 25_000f, 50_000f, 100_000f)
    val needed = maxDistanceM * 1.15f
    return steps.firstOrNull { it >= needed } ?: needed
}

internal fun formatDistance(meters: Float): String =
    if (meters < 1000f) "${meters.toInt()} m" else String.format(Locale.US, "%.1f km", meters / 1000f)

internal fun compass(bearing: Float): String {
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return directions[(((bearing % 360f + 360f + 22.5f) % 360f) / 45f).toInt() % 8]
}
