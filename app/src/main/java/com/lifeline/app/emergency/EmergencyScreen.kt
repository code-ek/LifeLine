package com.lifeline.app.emergency

import android.Manifest
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeline.app.home.ScreenHeader
import kotlinx.coroutines.delay
import java.util.Locale

private val SosRed = Color(0xFFD70015)

@Composable
fun EmergencyScreen() {
    val context = LocalContext.current
    val alerts by EmergencyRuntime.alerts.collectAsStateWithLifecycle()
    val locationSource = remember { EmergencyLocationSource(context) }

    var selectedType by rememberSaveable { mutableStateOf(EmergencyType.MEDICAL) }
    var description by rememberSaveable { mutableStateOf("") }
    var shareLocation by rememberSaveable { mutableStateOf(true) }
    var hasPermission by remember { mutableStateOf(locationSource.hasPermission()) }
    var myLocation by remember { mutableStateOf<Location?>(null) }
    var confirming by remember { mutableStateOf(false) }
    var sendFailed by remember { mutableStateOf(false) }
    var peers by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasPermission = locationSource.hasPermission() }

    DisposableEffect(shareLocation, hasPermission) {
        if (shareLocation && hasPermission) locationSource.start { myLocation = it }
        else locationSource.stop()
        onDispose { locationSource.stop() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            peers = EmergencyRuntime.nearbyPeerCount()
            now = System.currentTimeMillis()
            delay(2_000)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(title = "Emergency SOS", subtitle = "Alerts travel phone to phone, no internet needed") {
                MeshStatus(peers)
            }
        }

        item {
            SosComposer(
                selectedType = selectedType,
                onTypeSelected = { selectedType = it },
                description = description,
                onDescriptionChange = { description = it.take(SosCodec.MAX_DESCRIPTION_CHARS) },
                shareLocation = shareLocation,
                onShareLocationChange = { shareLocation = it },
                locationStatus = locationStatus(shareLocation, hasPermission, myLocation, locationSource),
                showPermissionButton = shareLocation && !hasPermission,
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                sendFailed = sendFailed,
                onSendClick = { confirming = true }
            )
        }

        item {
            Text(
                "SOS alerts on the mesh",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (alerts.isEmpty()) {
            item {
                Text(
                    "No alerts yet. Alerts from nearby devices appear here, even ones relayed through other phones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            items(alerts, key = { it.payload.id }) { alert ->
                SosAlertCard(alert, myLocation, now)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Send SOS to everyone nearby?") },
            text = {
                val withLocation = shareLocation && myLocation != null
                Text(
                    "A ${selectedType.label.lowercase(Locale.US)} emergency alert will be broadcast " +
                        "over the mesh and relayed phone to phone" +
                        if (withLocation) ", including your GPS location." else ", without your location."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    val location = if (shareLocation) myLocation?.toSosLocation() else null
                    val sent = EmergencyRuntime.sendSos(context, selectedType, description, location)
                    sendFailed = !sent
                    if (sent) description = ""
                }) { Text("SEND SOS", color = SosRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MeshStatus(peers: Int) {
    val color = if (peers > 0) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = if (peers == 1) "● 1 device nearby" else "● $peers devices nearby",
        color = color,
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun SosComposer(
    selectedType: EmergencyType,
    onTypeSelected: (EmergencyType) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    shareLocation: Boolean,
    onShareLocationChange: (Boolean) -> Unit,
    locationStatus: String,
    showPermissionButton: Boolean,
    onRequestPermission: () -> Unit,
    sendFailed: Boolean,
    onSendClick: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(EmergencyType.entries) { type ->
                FilterChip(
                    selected = type == selectedType,
                    onClick = { onTypeSelected(type) },
                    label = { Text(type.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SosRed,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("What's happening? (optional)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Share my location", style = MaterialTheme.typography.bodyLarge)
                Text(
                    locationStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (showPermissionButton) {
                    TextButton(onClick = onRequestPermission) { Text("Allow location") }
                }
            }
            Switch(checked = shareLocation, onCheckedChange = onShareLocationChange)
        }

        Button(
            onClick = onSendClick,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SosRed, contentColor = Color.White)
        ) {
            Text("SEND SOS", fontSize = 22.sp, fontWeight = FontWeight.Black)
        }

        if (sendFailed) {
            Text(
                "Could not reach the mesh service. Make sure Bluetooth is on and LifeLine has finished setup.",
                color = SosRed,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SosAlertCard(alert: SosAlert, myLocation: Location?, now: Long) {
    val sos = alert.payload
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alert.isLocal) MaterialTheme.colorScheme.surfaceVariant
            else SosRed.copy(alpha = 0.12f)
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = SosRed, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "SOS · ${sos.type.label.uppercase(Locale.US)}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    formatAge(now - sos.timestampMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (alert.isLocal) "Sent by you"
                else "From ${alert.senderNickname ?: alert.senderPeerID?.take(8) ?: "unknown"}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (sos.description.isNotBlank()) {
                Text(sos.description, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                describeLocation(sos.location, myLocation, alert.isLocal),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun locationStatus(
    shareLocation: Boolean,
    hasPermission: Boolean,
    myLocation: Location?,
    source: EmergencyLocationSource
): String = when {
    !shareLocation -> "Your location will not be included"
    !hasPermission -> "Location permission needed"
    myLocation != null -> buildString {
        append("GPS fix")
        if (myLocation.hasAccuracy()) append(" ±${myLocation.accuracy.toInt()} m")
        append(" · ").append(formatLatLon(myLocation.latitude, myLocation.longitude))
    }
    !source.isGpsEnabled() -> "GPS is off. Turn on Location in system settings"
    else -> "Getting GPS fix… works offline, faster outdoors"
}

private fun describeLocation(location: SosLocation?, myLocation: Location?, isLocal: Boolean): String {
    location ?: return "No location shared"
    return buildString {
        append("📍 ").append(formatLatLon(location.latitude, location.longitude))
        location.accuracyMeters?.let { append(" (±${it.toInt()} m)") }
        if (myLocation != null && !isLocal) {
            val result = FloatArray(2)
            Location.distanceBetween(
                myLocation.latitude, myLocation.longitude,
                location.latitude, location.longitude,
                result
            )
            append(" · ").append(formatDistance(result[0])).append(' ').append(compass(result[1]))
        }
    }
}

private fun formatLatLon(lat: Double, lon: Double): String =
    String.format(Locale.US, "%.5f, %.5f", lat, lon)

private fun formatDistance(meters: Float): String =
    if (meters < 1000f) "${meters.toInt()} m away" else String.format(Locale.US, "%.1f km away", meters / 1000f)

private fun compass(bearing: Float): String {
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return directions[(((bearing + 360f + 22.5f) % 360f) / 45f).toInt() % 8]
}

private fun formatAge(ageMs: Long): String {
    val seconds = (ageMs / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 86_400 -> "${seconds / 3600} h ago"
        else -> "${seconds / 86_400} d ago"
    }
}
