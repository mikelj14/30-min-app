package dev.thirtymin.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** Design 1d: one month of mornings, Monday first, plus that month's totals. */
@Composable
fun HistoryScreen(days: Map<LocalDate, Outcome>, today: LocalDate) {
    val current = YearMonth.from(today)
    var month by rememberSaveable { mutableStateOf(current) }
    val stats = monthStats(month, days, today)

    Column(
        Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("History", style = MaterialTheme.typography.headlineMedium)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = "Previous month",
                    modifier = Modifier.rotate(180f)
                )
            }
            Text(
                month.format(DateTimeFormatter.ofPattern("LLLL yyyy")),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < current) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = "Next month"
                )
            }
        }

        Calendar(month, days, today)
        Legend()

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val rate = if (stats.tracked == 0) "–" else "${stats.done * 100 / stats.tracked}%"
            StatCard(
                "${stats.done}",
                "of ${stats.tracked}",
                "Mornings this month",
                Modifier.weight(1f)
            )
            StatCard(rate, "", "Completion rate", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Calendar(month: YearMonth, days: Map<LocalDate, Outcome>, today: LocalDate) {
    val c = MaterialTheme.colorScheme
    val blanks = month.atDay(1).dayOfWeek.value - 1 // Monday = 1
    val cells = List(blanks) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW, LocalConfiguration.current.locales[0]),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = c.onSurfaceVariant
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        week.getOrNull(i)?.let {
                            DayCell(it, dayMark(it, days, today), it == today)
                        }
                    }
                }
            }
        }
    }
}

/** 44 dp circle: done = green fill, missed = dashed ring, today = amber ring. */
@Composable
private fun DayCell(date: LocalDate, mark: DayMark, isToday: Boolean) {
    val c = MaterialTheme.colorScheme
    val done = mark == DayMark.DONE
    val text = when {
        done && isToday -> c.onTertiary
        done -> c.onTertiaryContainer
        mark == DayMark.MISSED || isToday -> c.onSurfaceVariant
        else -> UNTRACKED
    }
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(44.dp)) {
            val ring = 2.dp.toPx()
            // Inner circle leaves a 2 dp gap inside the today ring.
            val inner = size.minDimension / 2 - if (isToday) ring * 2 else 0f
            when (mark) {
                DayMark.DONE -> drawCircle(if (isToday) c.tertiary else c.tertiaryContainer, inner)

                DayMark.MISSED -> drawCircle(
                    c.outlineVariant,
                    inner - 0.5.dp.toPx(),
                    style = Stroke(
                        1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                        )
                    )
                )

                DayMark.NONE -> Unit
            }
            if (isToday) {
                drawCircle(
                    c.primary,
                    size.minDimension / 2 - ring / 2,
                    style = Stroke(ring)
                )
            }
        }
        Text(
            "${date.dayOfMonth}",
            color = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (done && isToday) FontWeight.Bold else null
        )
    }
}

@Composable
private fun Legend() {
    val c = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem("Completed") { drawCircle(c.tertiaryContainer) }
        LegendItem("Missed") {
            drawCircle(
                c.outline,
                style = Stroke(
                    1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
                )
            )
        }
        LegendItem("Today") { drawCircle(c.primary, style = Stroke(2.dp.toPx())) }
    }
}

@Composable
private fun LegendItem(label: String, dot: DrawScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(12.dp), onDraw = dot)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatCard(value: String, suffix: String, label: String, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Surface(modifier, shape = MaterialTheme.shapes.large, color = c.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(value, style = MaterialTheme.typography.headlineLarge)
                if (suffix.isNotEmpty()) {
                    Text(
                        suffix,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
    }
}

// Design: future / untracked day numbers, dimmer than onSurfaceVariant.
private val UNTRACKED = Color(0xFF4A443B)
