package com.lifeline.app.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeline.app.home.ScreenHeader

/** Offline emergency assistant: an on-device AI model when downloaded, else the built-in guide. */
@Composable
fun AssistantScreen() {
    val context = LocalContext.current
    ModelManager.init(context) // idempotent; loads saved model state
    val history = AssistantSession.items
    val status by AssistantSession.status.collectAsStateWithLifecycle()
    val states by ModelManager.states.collectAsStateWithLifecycle()
    val selectedId by ModelManager.selected.collectAsStateWithLifecycle()
    val activeModel = ModelCatalog.byId(selectedId)?.takeIf { states[it.id]?.status == ModelStatus.READY }
    val downloading = states.values.any { it.status == ModelStatus.DOWNLOADING }
    var input by rememberSaveable { mutableStateOf("") }
    var showModels by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    fun ask(question: String) {
        if (question.isBlank() || status != null) return
        input = ""
        // Give the answer the whole screen.
        focusManager.clearFocus()
        AssistantSession.ask(context, question)
    }

    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.size)
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime)) {
        ScreenHeader(
            title = "Assistant",
            subtitle = if (activeModel != null) "Offline AI on this phone · ask anything"
            else "Works offline · first-aid and disaster guidance"
        ) {
            AssistChip(
                onClick = { showModels = true },
                label = { Text(if (activeModel != null) "AI on" else if (downloading) "Downloading…" else "Guide") },
                leadingIcon = {
                    Icon(
                        if (activeModel != null) Icons.Filled.AutoAwesome else Icons.Filled.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Intro(hasModel = activeModel != null, downloading = downloading) { showModels = true } }
            items(history) { item ->
                when (item) {
                    is ChatItem.Question -> QuestionBubble(item.text)
                    is ChatItem.GuideAnswer -> GuideBubble(item)
                    is ChatItem.AiAnswer -> AiBubble(item)
                }
            }
        }

        status?.let { text ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp).weight(1f))
                TextButton(onClick = { AssistantSession.stop() }) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Stop")
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(FirstAidGuide.suggestions) { suggestion ->
                SuggestionChip(onClick = { ask(suggestion) }, label = { Text(suggestion) }, enabled = status == null)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask me anything…") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { ask(input) })
            )
            FilledIconButton(
                onClick = { ask(input) },
                enabled = status == null,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask")
            }
        }
    }

    if (showModels) ModelSheet(onDismiss = { showModels = false })
}

@Composable
private fun Intro(hasModel: Boolean, downloading: Boolean, onSetUp: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
        Text("Ask anything, from emergencies to general survival tips", fontWeight = FontWeight.SemiBold)
        Text(
            if (hasModel) "Answers come from an AI model running on this phone, checked against a built-in first-aid guide. " +
                "It's general guidance, not a replacement for trained responders."
            else "Answers come from a built-in guide on this phone, so they work with no signal. " +
                "Download the offline AI model to unlock any topic.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!hasModel) {
            Button(onClick = onSetUp) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (downloading) "  Downloading offline AI…" else "  Get offline AI (one-time download)")
            }
        }
    }
    }
}

@Composable
private fun QuestionBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun AnswerContainer(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth(0.92f)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) { content() }
}

@Composable
private fun SourceLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AiBubble(item: ChatItem.AiAnswer) {
    AnswerContainer {
        SourceLabel("✦ Offline AI · ${item.modelName.substringAfter("· ")}")
        Text(if (item.text.isEmpty() && !item.done) "…" else item.text)
    }
}

@Composable
private fun GuideBubble(item: ChatItem.GuideAnswer) {
    val reply = item.reply
    AnswerContainer {
        SourceLabel("Built-in first-aid guide")
        item.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        Text(reply.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (reply.matched) {
            reply.steps.dropLast(1).forEachIndexed { index, step ->
                Row {
                    Text("${index + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 22.dp))
                    Text(step)
                }
            }
            Text(
                reply.steps.last(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            reply.steps.forEach { Text(it) }
        }
    }
}
