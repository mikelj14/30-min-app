package dev.thirtymin.app

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * How it works: one idea per page, each with a small working copy of the real
 * control, so the first morning is a rehearsal, not a surprise.
 * Shown once after setup; Settings › How it works reopens it.
 */
@Composable
fun TutorialScreen(schedule: Schedule, onDone: () -> Unit) {
    val pages = 4
    val pager = rememberPagerState { pages }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages - 1
    val c = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            if (!last) {
                TextButton(onClick = onDone, Modifier.align(Alignment.CenterEnd)) {
                    Text("Skip", color = c.onSurfaceVariant)
                }
            }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            when (page) {
                0 -> UnlockPage(schedule)
                1 -> CallsPage()
                2 -> ExitPage()
                else -> StreakPage()
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            repeat(pages) { i ->
                val on = i == pager.currentPage
                val w by animateDpAsState(if (on) 20.dp else 6.dp, label = "dot")
                Box(
                    Modifier.size(w, 6.dp).background(
                        if (on) c.primary else c.outlineVariant,
                        CircleShape
                    )
                )
            }
        }
        Button(
            onClick = {
                if (last) {
                    onDone()
                } else {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            },
            Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (last) "Go to Today" else "Next", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Demo on top, words under it, then one line that answers what the user just did. */
@Composable
private fun Page(
    title: String,
    body: String,
    feedback: String,
    done: Boolean,
    demo: @Composable () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val tint by animateColorAsState(if (done) c.tertiary else c.onSurfaceVariant, label = "tint")
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.heightIn(min = 240.dp), contentAlignment = Alignment.Center) { demo() }
        Spacer(Modifier.height(32.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = c.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        // Fixed height: the page does not jump when the line changes.
        AnimatedContent(
            feedback,
            Modifier.heightIn(min = 40.dp),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "feedback"
        ) {
            Text(
                it,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Tap the ring = the first unlock: it drains like a real block, then turns green. */
@Composable
private fun UnlockPage(s: Schedule) {
    val c = MaterialTheme.colorScheme
    val fmt = DateTimeFormatter.ofPattern(
        if (DateFormat.is24HourFormat(LocalContext.current)) "H:mm" else "h:mm a"
    )
    val start = s.start.format(fmt)
    val free = s.start.plusMinutes(s.minutes.toLong()).format(fmt)
    val left = remember { Animatable(1f) }
    // 0 locked, 1 running, 2 free
    var state by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    Page(
        "Your first unlock starts it",
        "Unlock between $start and ${s.end.format(fmt)} and the next ${s.minutes} minutes " +
            "stay quiet. Outside that window, nothing happens.",
        when (state) {
            0 -> "Tap the ring to unlock"
            1 -> "Blocked until $free"
            else -> "Free at $free. Tap to watch again"
        },
        done = state == 2
    ) {
        Box(
            Modifier
                .size(220.dp)
                .clip(CircleShape)
                .clickable(enabled = state != 1, onClickLabel = "Unlock") {
                    scope.launch {
                        state = 1
                        left.snapTo(1f)
                        // ponytail: demo compresses the block to 3 s; real length shown in text.
                        left.animateTo(0f, tween(3_000, easing = LinearEasing))
                        state = 2
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            CountdownRing(
                if (state == 2) 1f else left.value,
                220.dp,
                if (state == 2) c.tertiary else c.primary,
                animate = false
            )
            when (state) {
                0 -> Icon(
                    painterResource(R.drawable.ic_lock),
                    "Locked phone",
                    Modifier.size(40.dp),
                    tint = c.onSurfaceVariant
                )

                1 -> Text(
                    "${(left.value * s.minutes).toInt().coerceAtLeast(1)} min",
                    style = MaterialTheme.typography.headlineLarge
                )

                else -> Icon(
                    painterResource(R.drawable.ic_check),
                    "Block finished",
                    Modifier.size(48.dp),
                    tint = c.tertiary
                )
            }
        }
    }
}

/** The real phone button: a tap does nothing, a hold "opens the dialer". */
@Composable
private fun CallsPage() {
    val c = MaterialTheme.colorScheme
    // 0 untouched, 1 tapped, 2 held
    var state by remember { mutableIntStateOf(0) }
    val ring by animateColorAsState(
        if (state ==
            2
        ) {
            c.tertiary
        } else {
            c.outlineVariant
        },
        label = "ring"
    )
    Page(
        "Calls always get through",
        "During a block the phone icon sits in the top corner. Hold it to open the dialer. " +
            "Leave the phone app and the block comes back.",
        when (state) {
            0 -> "Touch and hold the phone"
            1 -> "A tap does nothing. Hold it a little longer"
            else -> "Dialer opens. Your block keeps running"
        },
        done = state == 2
    ) {
        Surface(
            Modifier.size(160.dp),
            shape = CircleShape,
            color = c.surfaceContainerLow,
            border = BorderStroke(1.dp, ring)
        ) {
            Box(contentAlignment = Alignment.Center) {
                PhoneButton(onPhone = { state = 2 }, Modifier, onTap = {
                    if (state !=
                        2
                    ) {
                        state = 1
                    }
                })
            }
        }
    }
}

/** The real exit control, practised: five taps, three seconds apart at most. */
@Composable
private fun ExitPage() {
    var taps by rememberExitTaps()
    val reached = taps >= EXIT_TAPS
    Page(
        "For real emergencies",
        "Tap Emergency exit five times, then confirm. It ends the block and your streak " +
            "starts over, so it never happens by accident.",
        when {
            reached -> "That's it. Next comes a confirm card"
            taps > 0 -> "Keep going. A 3 second pause starts over"
            else -> "Try it here. Nothing ends"
        },
        done = reached
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EmergencyExit(taps, onTap = { if (reached) taps = 0 else taps++ })
            Spacer(Modifier.height(8.dp))
            // Same height either way, so the button above does not move.
            TextButton(onClick = {
                taps = 0
            }, Modifier.alpha(if (reached) 1f else 0f), enabled = reached) {
                Text("Practise again", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Tap the week: each finished morning lights a day and adds one. */
@Composable
private fun StreakPage() {
    val c = MaterialTheme.colorScheme
    var done by remember { mutableIntStateOf(0) }
    var missed by remember { mutableStateOf(false) }
    Page(
        "Mornings add up",
        "Each finished block adds one to your streak. Ending early or skipping a morning " +
            "starts it over. History shows every month.",
        when {
            missed -> "Missed a day: the streak starts over"
            done == 0 -> "Tap the week to finish a morning"
            done < 6 -> "$done in a row"
            else -> "6 in a row. Tap once more to miss a day"
        },
        done = done > 0 && !missed
    ) {
        Column(
            Modifier.clickable(onClickLabel = "Finish a morning") {
                when {
                    missed -> {
                        missed = false
                        done = 0
                    }

                    done < 6 -> done++

                    else -> missed = true
                }
            }.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(if (missed) 0 else done, label = "streak") {
                Text("$it", style = MaterialTheme.typography.displayLarge)
            }
            Text(
                if (missed || done != 1) "mornings" else "morning",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(7) { i ->
                    Day(
                        when {
                            i < done -> DayMark.DONE
                            missed && i == done -> DayMark.MISSED
                            else -> DayMark.NONE
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Day(mark: DayMark) {
    val c = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        when (mark) {
            DayMark.DONE -> c.tertiaryContainer
            else -> Color.Transparent
        },
        label = "day"
    )
    Box(
        Modifier
            .size(32.dp)
            .background(fill, CircleShape)
            .border(1.dp, if (mark == DayMark.MISSED) c.error else c.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (mark == DayMark.DONE) {
            Icon(
                painterResource(R.drawable.ic_check),
                null,
                Modifier.size(18.dp),
                tint = c.tertiary
            )
        }
    }
}
