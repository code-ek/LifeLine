package com.lifeline.app.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** "Talk as…": switch between your name and anonymous, or change your name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentitySheet(identity: IdentityState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(identity.name.isBlank()) }
    var draft by remember { mutableStateOf(identity.name) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Talk as…", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Pick how people see you. You can change this any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (identity.name.isNotBlank()) {
                Choice(
                    name = identity.name,
                    detail = "People see your name",
                    selected = !identity.anonymous
                ) { IdentityStore.setAnonymous(context, false); onDismiss() }
            }
            Choice(
                name = identity.alias,
                detail = "Your name is hidden",
                selected = identity.anonymous || identity.name.isBlank()
            ) { IdentityStore.setAnonymous(context, true); onDismiss() }

            if (editing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(Names.MAX_LENGTH) },
                        label = { Text(if (identity.name.isBlank()) "Add your name" else "New name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { draft = Names.random() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Casino, contentDescription = "Pick a random name")
                    }
                }
                Button(
                    onClick = { IdentityStore.rename(context, draft); onDismiss() },
                    enabled = Names.clean(draft) != null,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Use this name", style = MaterialTheme.typography.titleMedium) }
            } else {
                TextButton(onClick = { editing = true }) { Text("Change my name") }
            }

            Text(
                "Private chats you already started still know it's you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Choice(name: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerLowest)
            .border(if (selected) 3.dp else 2.dp, if (selected) scheme.primary else scheme.outlineVariant, shape)
            .clickable(onClickLabel = "Talk as $name", onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Avatar(name, size = 46.dp)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = if (selected) scheme.onPrimaryContainer else scheme.onSurface)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
        }
        Icon(
            if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (selected) "Selected" else null,
            tint = if (selected) scheme.primary else scheme.outline,
            modifier = Modifier.size(28.dp)
        )
    }
}
