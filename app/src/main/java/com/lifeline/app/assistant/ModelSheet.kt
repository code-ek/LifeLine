package com.lifeline.app.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModelManager.init(context) // idempotent; loads saved model state
    val states by ModelManager.states.collectAsStateWithLifecycle()
    val selected by ModelManager.selected.collectAsStateWithLifecycle()
    val ramGb = remember { ModelManager.deviceRamGb(context) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Offline AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Download a model once, over Wi-Fi if you can. It then runs entirely on this phone: " +
                        "no internet needed and nothing you ask leaves the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(ModelCatalog.models, key = { it.id }) { model ->
                ModelCard(
                    model = model,
                    state = states[model.id] ?: ModelState(),
                    isSelected = selected == model.id,
                    lowRam = ramGb + 0.5 < model.minRamGb,
                    onDownload = { ModelManager.download(context, model) },
                    onCancel = { ModelManager.cancel(context, model) },
                    onUse = { ModelManager.select(context, model.id) },
                    onDelete = { ModelManager.delete(context, model) }
                )
            }
            item {
                TextButton(onClick = { ModelManager.select(context, null); onDismiss() }) {
                    Text("Use the built-in guide only")
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: AiModel,
    state: ModelState,
    isSelected: Boolean,
    lowRam: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected && state.status == ModelStatus.READY) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (isSelected && state.status == ModelStatus.READY) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "In use", tint = Color(0xFF34C759))
                }
            }
            Text(model.summary, style = MaterialTheme.typography.bodySmall)
            Text(
                "${gb(model.sizeBytes)} download" + if (lowRam) " · this phone may not have enough memory" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (lowRam) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (state.status) {
                ModelStatus.NOT_DOWNLOADED, ModelStatus.FAILED -> {
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = onDownload) { Text(if (state.status == ModelStatus.FAILED) "Try again" else "Download") }
                }
                ModelStatus.DOWNLOADING -> {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${(state.progress * 100).toInt()}% · ${gb(state.downloadedBytes)} of ${gb(state.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onCancel) { Text("Cancel") }
                    }
                }
                ModelStatus.READY -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isSelected) Button(onClick = onUse) { Text("Use this model") }
                    else Text("Ready · in use", fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterVertically))
                    OutlinedButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}

private fun gb(bytes: Long): String =
    if (bytes >= 1_000_000_000L) String.format(Locale.US, "%.1f GB", bytes / 1e9)
    else String.format(Locale.US, "%.0f MB", bytes / 1e6)
