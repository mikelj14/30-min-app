package dev.thirtymin.app

import android.view.View
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay

/**
 * Compose needs a lifecycle and a saved-state registry from the view tree.
 * An Activity provides both; a Service window has neither, so this stands in.
 * RESUMED while the overlay exists, DESTROYED when the service ends.
 */
class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    fun attach(view: View) {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

/**
 * Design 1b. All state lives in [BlockerService]; this only draws it.
 * @param streak current streak before today, null while loading or unreadable.
 */
@Composable
fun BlockScreen(
    remainingMs: Long,
    totalMs: Long,
    streak: Int?,
    endedEarly: Boolean,
    completed: Boolean,
    onExit: () -> Unit,
    onPhone: () -> Unit,
    onContinue: () -> Unit
) {
    var taps by rememberExitTaps()
    val c = MaterialTheme.colorScheme

    // Surface sets the content color (onBackground); bare Text outside one is black.
    Surface(Modifier.fillMaxSize(), color = c.background) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            if (endedEarly) {
                Text(
                    "Block ended early. See you tomorrow morning.",
                    Modifier.align(Alignment.Center).padding(32.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                return@Box
            }
            if (completed) {
                CompleteScreen(totalMs / 60_000, streak, onContinue)
                return@Box
            }
            PhoneButton(onPhone, Modifier.align(Alignment.TopEnd).padding(12.dp))

            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Good morning", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(32.dp))
                Box(contentAlignment = Alignment.Center) {
                    CountdownRing(remainingMs.toFloat() / totalMs, 288.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Round up: never show 0:00 while still blocked.
                        val s = (remainingMs + 999) / 1_000
                        Text(
                            "%d:%02d".format(s / 60, s % 60),
                            style = MaterialTheme.typography.displayMedium
                        )
                        Text(
                            "of ${totalMs / 60_000} min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text(
                    "Let the day arrive slowly. Everything else can wait.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            EmergencyExit(
                taps,
                onTap = { if (taps < EXIT_TAPS) taps++ },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            )

            if (taps >= EXIT_TAPS) ExitConfirm(streak, onEnd = onExit, onKeep = { taps = 0 })
        }
    }
}

/** Design 1c: green ring fills, check fades in, streak +1, Continue. */
@Composable
private fun CompleteScreen(minutes: Long, streak: Int?, onContinue: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true } // starts the fill and the fade below
    val fill by animateFloatAsState(if (shown) 1f else 0f, tween(1_400), label = "fill")
    val check by animateFloatAsState(if (fill == 1f) 1f else 0f, tween(400), label = "check")

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            CountdownRing(fill, 184.dp, c.tertiary, animate = false)
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(64.dp).alpha(check),
                tint = c.tertiary
            )
        }
        Spacer(Modifier.height(32.dp))
        Text("Morning protected", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "You gave the first $minutes minutes of today to yourself.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Streak before today + this morning. Unknown (read failed) = tile left out.
            if (streak != null) Tile("${streak + 1}", "+1", "Streak", Modifier.weight(1f))
            Tile("$minutes", "min", "Protected today", Modifier.weight(1f))
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onContinue, Modifier.fillMaxWidth()) { Text("Continue") }
    }
}

@Composable
private fun Tile(value: String, suffix: String, label: String, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Surface(modifier, shape = MaterialTheme.shapes.large, color = c.surfaceContainerLow) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(value, style = MaterialTheme.typography.headlineLarge)
                Text(
                    suffix,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (suffix == "+1") c.tertiary else c.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}

/**
 * Drawn inside the overlay: a Compose AlertDialog opens its own window,
 * which a service overlay has no token for.
 */
@Composable
private fun ExitConfirm(streak: Int?, onEnd: () -> Unit, onKeep: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(c.scrim.copy(alpha = 0.6f))
            // Swallow taps, so buttons under the card cannot be pressed through it.
            .clickable(interactionSource = null, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Surface(
            Modifier.padding(24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = c.surfaceContainerHigh
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("End this morning's block?", style = MaterialTheme.typography.titleLarge)
                Text(
                    (
                        if (streak != null &&
                            streak > 0
                        ) {
                            "Your $streak-morning streak will start over tomorrow. "
                        } else {
                            ""
                        }
                        ) +
                        "Calls never need an exit: hold the Phone button.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEnd) { Text("End block", color = c.onSurfaceVariant) }
                    Button(onClick = onKeep) { Text("Keep going") }
                }
            }
        }
    }
}

/** Long press only, so a stray tap cannot hide the block. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneButton(onPhone: () -> Unit, modifier: Modifier, onTap: () -> Unit = {}) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier
            .combinedClickable(onClick = onTap, onLongClick = onPhone)
            .semantics { contentDescription = "Phone. Touch and hold to make a call." }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(painterResource(R.drawable.ic_call), null, Modifier.size(28.dp), tint = c.tertiary)
        Text("Hold", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
    }
}

/**
 * Track plus an arc from 12 o'clock, sweep = [fraction] of a full circle,
 * animated linearly over each 1 s tick so it moves smoothly.
 */
@Composable
fun CountdownRing(
    fraction: Float,
    size: Dp,
    color: Color = MaterialTheme.colorScheme.primary,
    // false: the caller already animates fraction.
    animate: Boolean = true
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val smooth = animateFloatAsState(
        fraction.coerceIn(0f, 1f),
        tween(1_000, easing = LinearEasing),
        label = "ring"
    )
    Canvas(Modifier.size(size)) {
        // Read inside the draw lambda: each animation frame only redraws the
        // canvas instead of recomposing this function.
        val p = if (animate) smooth.value else fraction
        val stroke = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(
            track,
            style = Stroke(4.dp.toPx()),
            radius =
                this.size.minDimension / 2 - 2.dp.toPx()
        )
        val inset = 2.dp.toPx()
        drawArc(
            color,
            -90f,
            360f * p,
            false,
            topLeft = Offset(inset, inset),
            size = Size(
                this.size.width - inset * 2,
                this.size.height - inset * 2
            ),
            style = stroke
        )
    }
}

const val EXIT_TAPS = 5

/** Tap count for [EmergencyExit]; taps must come within 3 s of each other, or it starts over. */
@Composable
fun rememberExitTaps(): MutableIntState {
    val taps = remember { mutableIntStateOf(0) }
    LaunchedEffect(taps.intValue) {
        if (taps.intValue in 1 until EXIT_TAPS) {
            delay(3_000)
            taps.intValue = 0
        }
    }
    return taps
}

/**
 * Outlined pill so it reads as a real button, kept quiet (no amber: not an action
 * we want). Dots fill per tap; the label counts down. Shared with the tutorial.
 */
@Composable
fun EmergencyExit(taps: Int, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            // Space kept while hidden, so the button does not jump on the first tap.
            Modifier.alpha(if (taps > 0) 1f else 0f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(EXIT_TAPS) { i ->
                Box(
                    Modifier.size(6.dp).background(
                        if (i < taps) c.onSurface else c.outlineVariant,
                        CircleShape
                    )
                )
            }
        }
        OutlinedButton(
            onClick = onTap,
            border = BorderStroke(1.dp, if (taps > 0) c.onSurfaceVariant else c.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.onSurface),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
        ) {
            Text(
                if (taps == 0 ||
                    taps >= EXIT_TAPS
                ) {
                    "Emergency exit"
                } else {
                    "Tap ${EXIT_TAPS - taps} more"
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
