package com.lifeline.app.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lifeline.app.core.ui.icon.LifeLineIcon

/**
 * First launch: pick a name (typed or random) or stay anonymous. Shown before the permission
 * screens. [onDone] gets the chosen name, or null for anonymous.
 */
@Composable
fun NameSetupScreen(onDone: (String?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    var name by rememberSaveable { mutableStateOf("") }
    val clean = Names.clean(name)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(scheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(LifeLineIcon, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(28.dp))
            }
            Text("LifeLine", style = MaterialTheme.typography.titleLarge, color = scheme.primary)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What should people call you?", style = MaterialTheme.typography.headlineMedium)
            Text(
                "This is the name nearby people see when you chat.",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Names.MAX_LENGTH) },
                    label = { Text("Your name") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.weight(1f)
                )
                OutlinedIconButton(
                    onClick = { name = Names.random() },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, scheme.outline),
                    modifier = Modifier.size(60.dp).padding(top = 6.dp)
                ) {
                    Icon(Icons.Filled.Casino, contentDescription = "Pick a random name", tint = scheme.primary)
                }
            }
            Text("Tap the dice for a random name.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }

        Button(
            onClick = { onDone(clean) },
            enabled = clean != null,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Text(if (clean != null) "Continue as $clean" else "Type a name to continue", style = MaterialTheme.typography.titleMedium)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text("or", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        OutlinedButton(
            onClick = { onDone(null) },
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, scheme.primary),
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) {
            Icon(Icons.Filled.VisibilityOff, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Text("Stay anonymous", style = MaterialTheme.typography.titleMedium)
        }

        InfoNote(
            text = "You can switch between your name and anonymous any time while chatting.",
            icon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = scheme.onPrimaryContainer) }
        )
    }
}
