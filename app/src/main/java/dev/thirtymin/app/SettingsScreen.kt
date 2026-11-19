package dev.thirtymin.app

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Design 1e. Every change saves at once through [onSave], which returns null
 * when stored, or a message (invalid input, or locked during a block); the
 * screen then keeps showing the unsaved value with the message under it.
 * Success shows a green "Saved" line: without a Save button, users need it.
 */
// Opt-in: custom Slider thumb/track slots are still experimental in Material 3.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    saved: Schedule,
    onSave: (LocalTime, LocalTime, Int) -> String?,
    setupSummary: String,
    onOpenSetup: () -> Unit,
    onOpenTutorial: () -> Unit
) {
    var start by remember(saved) { mutableStateOf(saved.start) }
    var end by remember(saved) { mutableStateOf(saved.end) }
    var minutes by remember(saved) { mutableFloatStateOf(saved.minutes.toFloat()) }
    var picking by remember { mutableStateOf<Picking?>(null) }
    // null = nothing to say yet; error = shown in the error color.
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val timeFormat = DateTimeFormatter.ofPattern(if (is24h) "H:mm" else "h:mm a")
    val save = {
        val problem = onSave(start, end, minutes.roundToInt())
        error = problem != null
        message = problem ?: "Saved. Applies from the next window."
    }

    Column(
        Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Section("Wake window") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeTile("From", start.format(timeFormat), Modifier.weight(1f)) {
                    picking = Picking.START
                }
                TimeTile("Until", end.format(timeFormat), Modifier.weight(1f)) {
                    picking = Picking.END
                }
            }
            Hint(
                "The block starts at your first unlock inside this window. " +
                    "Outside it, 30-Min stays quiet."
            )
        }

        Section("Block length") {
            Card {
                Column(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)
                ) {
                    val m = minutes.roundToInt()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatMinutes(m),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Text(
                            "Unlock ${start.format(timeFormat)} → free " +
                                start.plusMinutes(m.toLong()).format(timeFormat),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = minutes,
                        onValueChange = { minutes = it },
                        onValueChangeFinished = save,
                        valueRange = BLOCK_MINUTES.first.toFloat()..BLOCK_MINUTES.last.toFloat(),
                        // 5-minute stops: 5, 10, ... 120 (steps = stops between the ends).
                        steps = (BLOCK_MINUTES.last - BLOCK_MINUTES.first) / 5 - 1,
                        thumb = { Thumb() },
                        track = {
                            Track(
                                (minutes - BLOCK_MINUTES.first) /
                                    (BLOCK_MINUTES.last - BLOCK_MINUTES.first)
                            )
                        }
                    )
                    Row(Modifier.fillMaxWidth()) {
                        SmallLabel("${BLOCK_MINUTES.first} min", Modifier.weight(1f))
                        SmallLabel("${BLOCK_MINUTES.last} min")
                    }
                }
            }
        }

        message?.let {
            val c = MaterialTheme.colorScheme
            Text(
                it,
                Modifier.padding(horizontal = 4.dp),
                color = if (error) c.error else c.tertiary
            )
        }

        Section("This phone") {
            Card {
                Column {
                    LinkRow(R.drawable.ic_smartphone, "Phone setup", setupSummary, onOpenSetup)
                    HorizontalDivider(
                        Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    LinkRow(
                        R.drawable.ic_lightbulb,
                        "How it works",
                        "Unlock, calls, exit and streaks, with practice",
                        onOpenTutorial
                    )
                    HorizontalDivider(
                        Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Row(
                        Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_lock),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column {
                            Text("Private by design", style = MaterialTheme.typography.titleSmall)
                            Hint("No account. Your mornings never leave this phone.", inset = 0.dp)
                        }
                    }
                }
            }
        }
    }

    picking?.let { which ->
        TimeDialog(
            initial = if (which == Picking.START) start else end,
            is24h = is24h,
            onDismiss = { picking = null },
            onPick = {
                if (which == Picking.START) start = it else end = it
                picking = null
                save()
            }
        )
    }
}

@Composable
private fun LinkRow(@DrawableRes icon: Int, title: String, hint: String, onClick: () -> Unit) = Row(
    Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Icon(painterResource(icon), contentDescription = null, tint = tint)
    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Hint(hint, inset = 0.dp)
    }
    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = tint)
}

private enum class Picking { START, END }

private val TRACK = Color(0xFF3A352E)

/** Design 1e slider: 24 dp amber dot with a soft 6 dp halo. */
@Composable
private fun Thumb() {
    val c = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(36.dp)) {
        drawCircle(c.copy(alpha = 0.14f), radius = 18.dp.toPx())
        drawCircle(c, radius = 12.dp.toPx())
    }
}

/** Thin 4 dp track, 8 dp amber fill up to [fraction]. */
@Composable
private fun Track(fraction: Float) {
    val c = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val y = size.height / 2
        drawLine(TRACK, Offset(0f, y), Offset(size.width, y), 4.dp.toPx(), StrokeCap.Round)
        drawLine(c, Offset(0f, y), Offset(size.width * fraction, y), 8.dp.toPx(), StrokeCap.Round)
    }
}

/** 45 -> "45 min", 60 -> "1 h", 90 -> "1 h 30 min". */
fun formatMinutes(m: Int): String = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60} min"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) = Surface(
    Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
    content = content
)

@Composable
private fun TimeTile(label: String, time: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Hint(label)
            Text(time, style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable
private fun Hint(text: String, inset: Dp = 4.dp) = Text(
    text,
    Modifier.padding(horizontal = inset),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun SmallLabel(text: String, modifier: Modifier = Modifier) = Text(
    text,
    modifier,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.outline
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    initial: LocalTime,
    is24h: Boolean,
    onDismiss: () -> Unit,
    onPick: (LocalTime) -> Unit
) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24h)
    // The dial is quick for rough times but hard to hit an exact minute on;
    // typing is exact. Same state, so switching keeps the chosen time.
    var typing by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (typing) TimeInput(state) else TimePicker(state)
                TextButton(onClick = { typing = !typing }, Modifier.align(Alignment.Start)) {
                    Text(if (typing) "Use dial" else "Type time")
                }
            }
        }
    )
}
