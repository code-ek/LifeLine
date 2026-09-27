package com.lifeline.app.translate

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeline.app.assistant.ModelManager
import com.lifeline.app.assistant.ModelSheet
import com.lifeline.app.home.ScreenHeader

/** Conversation translator: each person speaks or types in their own language. */
@Composable
fun TranslateScreen() {
    val context = LocalContext.current
    ModelManager.init(context)
    TranslateSession.init(context)
    val mine by TranslateSession.mine.collectAsStateWithLifecycle()
    val theirs by TranslateSession.theirs.collectAsStateWithLifecycle()
    val autoSpeak by TranslateSession.autoSpeak.collectAsStateWithLifecycle()
    val status by TranslateSession.status.collectAsStateWithLifecycle()
    val states by ModelManager.states.collectAsStateWithLifecycle()
    val selectedId by ModelManager.selected.collectAsStateWithLifecycle()
    val model = remember(states, selectedId) { ModelManager.activeModel(context) }
    val items = TranslateSession.items

    val listener = remember { Listener(context) }
    var listeningFor by remember { mutableStateOf<Party?>(null) }
    var partial by remember { mutableStateOf("") }
    var speechError by remember { mutableStateOf<String?>(null) }
    var typingAs by rememberSaveable { mutableStateOf(Party.ME) }
    var input by rememberSaveable { mutableStateOf("") }
    var showModels by remember { mutableStateOf(false) }
    var bigItem by remember { mutableStateOf<TranslationItem?>(null) }
    var pendingMic by remember { mutableStateOf<Party?>(null) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    DisposableEffect(Unit) { onDispose { listener.cancel() } }
    LaunchedEffect(items.size) { if (items.isNotEmpty()) listState.animateScrollToItem(items.size) }

    fun listen(who: Party) {
        if (listeningFor == who) { listener.finish(); return }
        speechError = null
        partial = ""
        listeningFor = who
        val language = if (who == Party.ME) mine else theirs
        listener.start(
            language,
            onPartial = { partial = it },
            onResult = { heard ->
                listeningFor = null; partial = ""
                TranslateSession.translate(context, who, heard)
            },
            onError = { message -> listeningFor = null; partial = ""; speechError = message }
        )
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val who = pendingMic
        pendingMic = null
        if (granted && who != null) listen(who) else if (!granted) speechError = "Microphone permission is needed to listen."
    }

    fun onMic(who: Party) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) listen(who) else { pendingMic = who; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }

    fun send() {
        if (input.isBlank()) return
        TranslateSession.translate(context, typingAs, input)
        input = ""
        focusManager.clearFocus()
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime)) {
        ScreenHeader(title = "Translate", subtitle = "Talk with someone in another language · offline") {
            IconButton(onClick = { TranslateSession.setAutoSpeak(context, !autoSpeak) }) {
                Icon(
                    if (autoSpeak) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (autoSpeak) "Reading translations aloud" else "Not reading aloud"
                )
            }
        }



        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LanguagePicker("You speak", mine, model, Modifier.weight(1f)) {
                TranslateSession.setLanguages(context, it, if (it == theirs) mine else theirs)
            }
            IconButton(onClick = { TranslateSession.swap(context) }) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = "Swap languages")
            }
            LanguagePicker("They speak", theirs, model, Modifier.weight(1f)) {
                TranslateSession.setLanguages(context, if (it == mine) theirs else mine, it)
            }
        }

        if (model == null) {
            NoModelCard { showModels = true }
        } else if (!Languages.isWellSupported(model, mine) || !Languages.isWellSupported(model, theirs)) {
            Text(
                "The Standard model is weak at ${listOf(mine, theirs).filterNot { Languages.isWellSupported(model, it) }.joinToString { it.name }}. " +
                    "Gemma 4 handles 140+ languages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).clickable { showModels = true }
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (items.isEmpty()) {
                item { EmptyHint(mine, theirs) }
            }
            items(items, key = { it.id }) { item ->
                TranslationBubble(item, onShow = { bigItem = item }, onReplay = { Speaker.speak(item.to, item.translated) })
            }
            if (listeningFor != null) {
                item {
                    Text(
                        "Listening… ${partial.ifBlank { "speak now" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        (speechError ?: status)?.let { text ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (status != null && speechError == null) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (speechError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp).weight(1f)
                )
                if (speechError != null) TextButton(onClick = { speechError = null }) { Text("OK") }
            }
        }

        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TranslateSession.quickPhrases) { phrase ->
                SuggestionChip(
                    onClick = { TranslateSession.translate(context, Party.ME, phrase, fromOverride = Languages.all.first()) },
                    label = { Text(phrase) },
                    enabled = model != null
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MicButton(mine, listening = listeningFor == Party.ME, primary = true, modifier = Modifier.weight(1f)) { onMic(Party.ME) }
            MicButton(theirs, listening = listeningFor == Party.THEM, primary = false, modifier = Modifier.weight(1f)) { onMic(Party.THEM) }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = true,
                onClick = { typingAs = if (typingAs == Party.ME) Party.THEM else Party.ME },
                label = { Text(if (typingAs == Party.ME) "Me" else "Them") }
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Type in ${(if (typingAs == Party.ME) mine else theirs).nativeName}…") },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                maxLines = 3,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() })
            )
            FilledIconButton(onClick = { send() }, enabled = model != null) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Translate")
            }
        }
    }

    bigItem?.let { item -> BigTextDialog(item) { bigItem = null } }
    if (showModels) ModelSheet(onDismiss = { showModels = false })
}

@Composable
private fun LanguagePicker(
    label: String,
    selected: Language,
    model: com.lifeline.app.assistant.AiModel?,
    modifier: Modifier,
    onPick: (Language) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected.nativeName, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Languages.all.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(language.label)
                            if (model != null && !Languages.isWellSupported(model, language)) {
                                Text("best with Gemma 4", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    onClick = { open = false; onPick(language) }
                )
            }
        }
    }
}

@Composable
private fun MicButton(language: Language, listening: Boolean, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = if (primary) ButtonDefaults.buttonColors()
    else ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )
    Button(onClick = onClick, modifier = modifier.height(56.dp), colors = colors, shape = RoundedCornerShape(16.dp)) {
        Icon(Icons.Filled.Mic, contentDescription = null)
        Text(
            if (listening) "  Done" else "  ${language.nativeName}",
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyHint(mine: Language, theirs: Language) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("How it works", fontWeight = FontWeight.SemiBold)
            Text(
                "Tap a mic, speak ${mine.name}, and it translates to ${theirs.name}. Tap any bubble to enlarge.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoModelCard(onSetUp: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Needs an offline AI model. Download once while online.",
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Button(onClick = onSetUp) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Get offline AI")
            }
        }
    }
}

@Composable
private fun TranslationBubble(item: TranslationItem, onShow: () -> Unit, onReplay: () -> Unit) {
    val mine = item.speaker == Party.ME
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    RoundedCornerShape(16.dp)
                )
                .clickable(enabled = item.translated.isNotBlank(), onClick = onShow)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${if (mine) "You" else "Them"} · ${item.from.nativeName} → ${item.to.nativeName}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.done && item.translated.isNotBlank()) {
                    IconButton(onClick = onReplay, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud", modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(
                item.translated.ifBlank { if (item.done) "-" else "..." },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(item.original, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Full-screen translation to hold up for the other person. */
@Composable
private fun BigTextDialog(item: TranslationItem, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).clickable(onClick = onClose)
                .verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(item.to.nativeName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(item.translated, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold)
            Text(item.original, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tap anywhere to close", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
