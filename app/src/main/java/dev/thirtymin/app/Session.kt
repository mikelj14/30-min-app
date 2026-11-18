package dev.thirtymin.app

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

// Pure session logic: no Android imports, so it runs in plain JVM unit tests.
// Android code feeds in the clock and stored values and acts on the answer.

/** User settings. Window is same-day only: [start] must be before [end]. */
data class Schedule(val start: LocalTime, val end: LocalTime, val minutes: Int) {
    // ponytail: no midnight-wrapping windows; morning use case never needs them.
    init {
        require(start < end) { "window must start before it ends" }
        require(minutes > 0) { "minutes must be positive" }
    }
}

enum class Phase { IDLE, ARMED, ACTIVE, DONE }

/**
 * Where we are, derived from facts instead of stored as a mutable state.
 * Deriving means no transition can be forgotten or left half-written.
 *
 * @param lastStart day the last block started (manual or automatic), or null.
 * @param activeUntil `elapsedRealtime` end of the current block, or null.
 * @param nowElapsed current `elapsedRealtime`. Monotonic, so wall-clock
 *   changes cannot shorten or extend a block.
 */
fun phase(
    now: LocalDateTime,
    nowElapsed: Long,
    schedule: Schedule,
    lastStart: LocalDate?,
    activeUntil: Long?
): Phase = when {
    activeUntil != null && nowElapsed < activeUntil -> Phase.ACTIVE
    lastStart == now.toLocalDate() -> Phase.DONE
    now.toLocalTime() >= schedule.start && now.toLocalTime() < schedule.end -> Phase.ARMED
    else -> Phase.IDLE
}

enum class ArmAction { BLOCK, WAIT, STOP }

/**
 * Armed service saw the alarm, a screen-on or an unlock: what now?
 * Only ARMED can block, so stale alarms (window over) and duplicates
 * (already blocked today, even before a process restart) both STOP.
 * "In use" = screen on AND past the lock screen.
 */
fun armAction(phase: Phase, interactive: Boolean, keyguardLocked: Boolean): ArmAction = when {
    phase != Phase.ARMED -> ArmAction.STOP
    interactive && !keyguardLocked -> ArmAction.BLOCK
    else -> ArmAction.WAIT
}

/** When to fire the next alarm: today's window start if still ahead, else tomorrow's. */
fun nextWindowStart(now: LocalDateTime, schedule: Schedule): LocalDateTime {
    val today = now.toLocalDate().atTime(schedule.start)
    return if (now < today) today else today.plusDays(1)
}

/**
 * [nextWindowStart] pinned to a time zone. Calendar math, not +24h, so a
 * DST change still fires at 06:00 local. A start inside a DST gap
 * (e.g. 02:30 on spring-forward night) moves forward by the gap length.
 */
fun nextAlarmAt(now: ZonedDateTime, schedule: Schedule): ZonedDateTime =
    nextWindowStart(now.toLocalDateTime(), schedule).atZone(now.zone)
