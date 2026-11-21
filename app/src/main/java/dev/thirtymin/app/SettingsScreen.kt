package dev.thirtymin.app

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
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
        // Friends quote this in bug reports (GitHub issue form asks for it).
        Hint("Version ${BuildConfig.VERSION_NAME}")
    }

    picking?.let { which ->
        TimeSheet(
            title = if (which == Picking.START) "Window opens" else "Window closes",
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

/**
 * Bottom sheet with hour and minute wheels. Minutes move in 5-minute steps:
 * a wake window never needs more, and big targets beat an exact dial.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSheet(
    title: String,
    initial: LocalTime,
    is24h: Boolean,
    onDismiss: () -> Unit,
    onPick: (LocalTime) -> Unit
) {
    var hour by remember { mutableIntStateOf(initial.hour) }
    var step by remember { mutableIntStateOf(initial.minute / 5) }
    val picked = LocalTime.of(hour, step * 5)
    val hourFormat = DateTimeFormatter.ofPattern(if (is24h) "HH" else "h a")
    val fullFormat = DateTimeFormatter.ofPattern(if (is24h) "H:mm" else "h:mm a")
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Box(contentAlignment = Alignment.Center) {
                // Selection band behind the middle row of both wheels.
                Surface(
                    Modifier.fillMaxWidth().height(WHEEL_ROW),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {}
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Wheel(24, initial.hour, {
                        LocalTime.of(it, 0).format(hourFormat)
                    }, "Hour", Modifier.weight(1f)) {
                        hour = it
                    }
                    Text(":", style = MaterialTheme.typography.headlineLarge)
                    Wheel(12, initial.minute / 5, {
                        "%02d".format(it * 5)
                    }, "Minute", Modifier.weight(1f)) {
                        step = it
                    }
                }
            }
            Button(
                onClick = { onPick(picked) },
                Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(
                    "Set ${picked.format(fullFormat)}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

private val WHEEL_ROW = 56.dp

/** Snapping vertical list; the row in the middle is the value. */
@Composable
private fun Wheel(
    count: Int,
    initial: Int,
    label: (Int) -> String,
    name: String,
    modifier: Modifier,
    onSelect: (Int) -> Unit
) {
    val state = rememberLazyListState(initial)
    val selected by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index
                ?: initial
        }
    }
    LaunchedEffect(selected) { onSelect(selected) }
    LazyColumn(
        modifier.height(WHEEL_ROW * 5).semantics {
            contentDescription = "$name, ${label(selected)}"
        },
        state = state,
        contentPadding = PaddingValues(vertical = WHEEL_ROW * 2),
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(count) { i ->
            val distance = abs(i - selected)
            Box(Modifier.height(WHEEL_ROW).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    label(i),
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (distance == 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline.copy(
                            alpha = if (distance ==
                                1
                            ) {
                                1f
                            } else {
                                0.45f
                            }
                        )
                    }
                )
            }
        }
    }
}
