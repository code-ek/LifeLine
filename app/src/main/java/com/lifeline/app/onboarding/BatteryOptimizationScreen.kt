package com.lifeline.app.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Asks, in plain words, to let LifeLine keep running so messages and SOS alerts get through. */
@Composable
fun BatteryOptimizationScreen(
    modifier: Modifier,
    status: BatteryOptimizationStatus,
    onDisableBatteryOptimization: () -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    isLoading: Boolean = false
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { BatteryOptimizationPreferenceManager.init(context) }

    Column(
        modifier = modifier.fillMaxSize().background(scheme.background).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
        }

        when (status) {
            BatteryOptimizationStatus.ENABLED -> {
                Text("Keep LifeLine running", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Your phone may pause LifeLine to save battery. Then messages and SOS alerts from nearby phones can't reach you.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant
                )
                Box(Modifier.weight(1f))
                Button(
                    onClick = onDisableBatteryOptimization,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Text("Keep it running", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Not now", style = MaterialTheme.typography.titleSmall)
                }
            }
            BatteryOptimizationStatus.DISABLED -> {
                Text("All set", style = MaterialTheme.typography.headlineMedium)
                CircularProgressIndicator()
            }
            BatteryOptimizationStatus.NOT_SUPPORTED -> {
                Text("All set", style = MaterialTheme.typography.headlineMedium)
                Text("Your phone lets LifeLine keep running.", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                Box(Modifier.weight(1f))
                Button(onClick = onRetry, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Text("Continue", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
