package com.lifeline.app.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** "LifeLine needs N things": one plain sentence per permission and a single Allow button. */
@Composable
fun PermissionExplanationScreen(
    modifier: Modifier,
    permissionCategories: List<PermissionCategory>,
    onContinue: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val needed = permissionCategories.count { !it.isGranted }.coerceAtLeast(1)

    Column(modifier = modifier.fillMaxSize().background(scheme.background).padding(horizontal = 24.dp, vertical = 24.dp)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (needed == 1) "LifeLine needs 1 thing" else "LifeLine needs $needed things",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                "So it can reach phones near you, even with no internet.",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            permissionCategories.forEach { category -> PermissionRow(category) }
        }
        Button(
            onClick = onContinue,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(60.dp)
        ) {
            Text("Allow", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PermissionRow(category: PermissionCategory) {
    val scheme = MaterialTheme.colorScheme
    val (title, reason) = plainWords(category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(iconFor(category.type), contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(reason, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        if (category.isGranted) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Already allowed", tint = scheme.primary)
        }
    }
}

private fun plainWords(category: PermissionCategory): Pair<String, String> = when (category.type) {
    PermissionType.NEARBY_DEVICES -> "Nearby devices" to "To find phones around you"
    PermissionType.PRECISE_LOCATION -> "Location" to "Android needs it for Bluetooth. We never track you."
    PermissionType.BACKGROUND_LOCATION -> "Location while closed" to "Keeps LifeLine working when the app is closed"
    PermissionType.MICROPHONE -> "Microphone" to "For voice messages"
    PermissionType.NOTIFICATIONS -> "Notifications" to "So you never miss a message or SOS"
    PermissionType.WIFI_AWARE -> "Nearby Wi‑Fi" to "A faster way to reach phones close by"
    PermissionType.BATTERY_OPTIMIZATION -> "Run in background" to "So LifeLine keeps working to reach others"
    PermissionType.OTHER -> category.type.nameValue to category.description
}

private fun iconFor(type: PermissionType): ImageVector = when (type) {
    PermissionType.NEARBY_DEVICES -> Icons.Filled.Bluetooth
    PermissionType.PRECISE_LOCATION -> Icons.Filled.LocationOn
    PermissionType.BACKGROUND_LOCATION -> Icons.Filled.MyLocation
    PermissionType.MICROPHONE -> Icons.Filled.Mic
    PermissionType.NOTIFICATIONS -> Icons.Filled.Notifications
    PermissionType.WIFI_AWARE -> Icons.Filled.Wifi
    PermissionType.BATTERY_OPTIMIZATION -> Icons.Filled.BatteryChargingFull
    PermissionType.OTHER -> Icons.Filled.Security
}
