package dev.thirtymin.app

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/**
 * Design 1a. [top] holds cards that need attention (lost alarm) and debug
 * controls, shown under the date.
 */
@Composable
fun HomeScreen(
    rows: List<SessionRecord>,
    now: LocalDateTime,
    schedule: Schedule,
    onOpenHistory: () -> Unit,
    top: @Composable () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val today = now.toLocalDate()
    val stats = homeStats(rows, today)
    val days = rows.outcomeMap()
    val time = DateTimeFormatter.ofPattern(
        if (DateFormat.is24HourFormat(LocalContext.current)) "H:mm" else "h:mm a"
    )

    Column(
        Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column {
            Text("30-Min", style = MaterialTheme.typography.titleMedium)
            Text(
                today.format(DateTimeFormatter.ofPattern("EEEE, d MMM")),
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant
            )
        }
        top()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Morning streak",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant
            )
            Row {
                Text(
                    "${stats.streak}",
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    if (stats.streak == 1) " morning" else " mornings",
                    fontSize = 22.sp,
                    modifier = Modifier.alignByBaseline()
                )
            }
            WeekStrip(days, today, onOpenHistory)
        }

        val todayRow = rows.firstOrNull { it.day == today.toEpochDay() }
        if (todayRow?.outcome == Outcome.COMPLETED && todayRow.endedAt != null) {
            InfoCard(
                R.drawable.ic_check,
                c.tertiary,
                "Completed today",
                "${clock(todayRow.startedAt, time)} – ${clock(todayRow.endedAt, time)} · " +
                    "${todayRow.plannedMinutes} min protected"
            )
        }
        // Today's block still possible = no block yet and the window has not ended.
        val todayStill = todayRow == null && now.toLocalTime() < schedule.end
        InfoCard(
            R.drawable.ic_wb_twilight,
            c.primary,
            "${if (todayStill) "Today" else "Tomorrow"} ${schedule.start.format(time)}",
            "First unlock after ${schedule.start.format(time)} · ${schedule.minutes} min"
        )

        // IntrinsicSize.Min + fillMaxHeight: both tiles as tall as the taller one.
        Row(
            Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatTile("${stats.longest}", "Longest streak", Modifier.weight(1f).fillMaxHeight())
            StatTile("${stats.mornings}", "Mornings completed", Modifier.weight(1f).fillMaxHeight())
        }
        StatTile(protectedTime(stats.minutes), "Time protected", Modifier.fillMaxWidth())
    }
}

/** Monday to Sunday of this week: green = done, ring = today, outline = still to come. */
@Composable
private fun WeekStrip(days: Map<LocalDate, Outcome>, today: LocalDate, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val monday = today.minusDays(today.dayOfWeek.value - 1L)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "This week. Opens History." }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        DayOfWeek.entries.forEachIndexed { i, dow ->
            val date = monday.plusDays(i.toLong())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    dow.getDisplayName(TextStyle.NARROW, LocalConfiguration.current.locales[0]),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.onSurfaceVariant
                )
                Canvas(Modifier.padding(top = 6.dp).size(16.dp)) {
                    val r = 6.dp.toPx()
                    when {
                        dayMark(date, days, today) == DayMark.DONE -> drawCircle(c.tertiary, r)

                        date >= today -> drawCircle(
                            c.outlineVariant,
                            r,
                            style = Stroke(1.5.dp.toPx())
                        )

                        else -> drawCircle(c.surfaceContainerHigh, r) // past, not done
                    }
                    if (date ==
                        today
                    ) {
                        drawCircle(c.primary, r + 2.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    icon: Int,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    body: String
) {
    val c = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = c.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = tint)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Surface(modifier, shape = MaterialTheme.shapes.large, color = c.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Start)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
    }
}

private fun clock(epochMs: Long, f: DateTimeFormatter): String =
    LocalTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault()).format(f)

/** 1935 -> "32 h 15 min", 45 -> "45 min". */
fun protectedTime(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}
