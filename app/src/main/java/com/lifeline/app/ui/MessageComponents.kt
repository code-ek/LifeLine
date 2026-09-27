package com.lifeline.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeline.app.R
import com.lifeline.app.model.LifeLineMessage
import com.lifeline.app.model.DeliveryStatus
import com.lifeline.app.ui.theme.LifeLineMotion
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


// VoiceNotePlayer moved to com.lifeline.app.ui.media.VoiceNotePlayer

/**
 * Message display components
 */

/**
 * Above this many simultaneous arrivals, entry animations are skipped.
 *
 * A history sync or a channel switch can append hundreds of messages in one frame. Animating each
 * would spend the entire frame budget on motion nobody asked to see, so a burst is adopted
 * silently and only conversational-pace arrivals animate.
 */
internal const val MaxAnimatedArrivals = 6

/**
 * Remembers which message ids have already been seen, so genuine arrivals can be told apart from
 * items merely scrolling back into view.
 *
 * This distinction is the whole reason the entry animation is usable: `LazyColumn` composes items
 * on demand, so animating on first composition would replay the animation for every old message
 * the user scrolled back to.
 */
internal class MessageArrivalTracker {
    val known = HashSet<String>()
    var seeded = false
}

/**
 * Ids that should animate in on this composition pass.
 *
 * Deliberately computed during composition rather than in a `LaunchedEffect`: effects run *after*
 * the frame's composition, by which point a new message's item has already composed and would
 * have missed its cue.
 */
internal fun MessageArrivalTracker.arrivals(messages: List<LifeLineMessage>): Set<String> {
    if (!seeded) {
        // First load adopts everything silently. A whole screenful animating on open reads as a
        // glitch, not a flourish.
        messages.forEach { known.add(it.id) }
        seeded = true
        return emptySet()
    }

    // A list with nothing in common with the last one is a different conversation, not a burst of
    // arrivals — /clear, or a switch the caller did not give us a distinct key for. Adopt it
    // silently rather than sliding in every message at once.
    val isWholesaleReplacement =
        messages.isNotEmpty() && known.isNotEmpty() && messages.none { it.id in known }

    // `HashSet.add` reports whether the id was new, so this both diffs and updates in one pass.
    val added = messages.filter { known.add(it.id) }

    if (known.size > messages.size) {
        // Messages disappeared (/clear, channel switch). Drop the stale ids so the set cannot
        // grow without bound and so re-added messages animate again.
        known.retainAll(messages.mapTo(HashSet(messages.size)) { it.id })
    }

    return when {
        isWholesaleReplacement -> emptySet()
        added.isEmpty() || added.size > MaxAnimatedArrivals -> emptySet()
        else -> added.mapTo(HashSet(added.size)) { it.id }
    }
}

/**
 * Per-check target colours for the delivery marker.
 *
 * Both checks always render — grey (disabled) until an acknowledgement turns them on — so a
 * status change recolours in place and never reflows text around it. Read receipts use the
 * app's primary green rather than a separate accent. [status] == null yields the all-grey
 * baseline used while a message is still being sent.
 */
private fun deliveryCheckColors(status: DeliveryStatus?, colorScheme: ColorScheme): Pair<Color, Color> {
    val grey = colorScheme.onSurface.copy(alpha = 0.35f)
    val green = colorScheme.primary
    return when (status) {
        is DeliveryStatus.Read -> green to green
        is DeliveryStatus.Delivered -> green to grey
        is DeliveryStatus.PartiallyDelivered -> green to grey
        is DeliveryStatus.Failed -> colorScheme.error to colorScheme.error
        else -> grey to grey
    }
}

/** Acknowledgement progress ordering, used to fire the pop only when the state advances. */
private fun deliveryCheckRank(status: DeliveryStatus): Int = when (status) {
    is DeliveryStatus.Read -> 3
    is DeliveryStatus.Delivered -> 2
    is DeliveryStatus.PartiallyDelivered -> 2
    is DeliveryStatus.Failed -> 1
    else -> 0
}

@Composable
fun DeliveryStatusIcon(status: DeliveryStatus) {
    val colorScheme = MaterialTheme.colorScheme
    val (firstTarget, secondTarget) = deliveryCheckColors(status, colorScheme)
    val first by animateColorAsState(
        targetValue = firstTarget,
        animationSpec = tween(LifeLineMotion.QUICK_MS),
        label = "firstCheckColor",
    )
    val second by animateColorAsState(
        targetValue = secondTarget,
        animationSpec = tween(LifeLineMotion.QUICK_MS),
        label = "secondCheckColor",
    )

    // Snappy micro pop when the state advances to (more) acknowledged. Keyed on the rank, not
    // the instance, because Delivered/Read carry timestamps that would retrigger it otherwise.
    val scale = remember { Animatable(1f) }
    LaunchedEffect(deliveryCheckRank(status)) {
        if (deliveryCheckRank(status) >= 2) {
            scale.snapTo(1.3f)
            scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 900f))
        }
    }

    val text = remember(first, second) {
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(color = first))
            append("✓")
            pop()
            pushStyle(androidx.compose.ui.text.SpanStyle(color = second))
            append("✓")
            pop()
        }
    }
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
    )
}
