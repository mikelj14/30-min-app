package dev.thirtymin.app

import java.time.LocalDate
import java.time.YearMonth

// Pure streak logic, like Session.kt: plain JVM, no Android or Room imports.

/**
 * How a day's block ended.
 * COMPLETED: timer ran out. QUIT: user's own choice (emergency exit, overlay
 * revoke). SYSTEM: nothing was written, so the process died (reboot,
 * force-stop, Samsung kill): not the user's fault.
 * Room stores these by name: never rename or remove one (old rows would fail
 * to load). Add new ones only with a schema version bump.
 */
enum class Outcome { COMPLETED, QUIT, SYSTEM }

/**
 * Streak counting rules (user decisions, phase 5.5):
 * COMPLETED adds one; SYSTEM is neutral (skipped, never breaks);
 * QUIT or a day with no record breaks it.
 */
private fun step(outcome: Outcome?): Int? = when (outcome) {
    Outcome.COMPLETED -> 1
    Outcome.SYSTEM -> 0
    Outcome.QUIT, null -> null
}

/**
 * Streak ending today. Today with no record yet does not break it (the
 * morning may still come), so the streak runs through yesterday until then.
 * A running block has no outcome yet and reads as SYSTEM: neutral, same effect.
 */
// ponytail: "today pending" lasts all day, also after the window ended; a
// missed today shows as broken only tomorrow. Pass the schedule if that matters.
fun currentStreak(days: Map<LocalDate, Outcome>, today: LocalDate): Int {
    var day = if (today in days) today else today.minusDays(1)
    var streak = 0
    while (true) {
        streak += step(days[day]) ?: return streak
        day = day.minusDays(1)
    }
}

/** Longest run ever, same rules as [currentStreak]. */
fun longestStreak(days: Map<LocalDate, Outcome>): Int {
    val first = days.keys.minOrNull() ?: return 0
    val last = days.keys.max()
    var best = 0
    var run = 0
    var day = first
    while (day <= last) {
        val s = step(days[day])
        run = if (s == null) 0 else run + s
        best = maxOf(best, run)
        day = day.plusDays(1)
    }
    return best
}

/** How the History calendar draws a day. NONE = no shape (neutral, pending, untracked or future). */
enum class DayMark { DONE, MISSED, NONE }

/**
 * Same rules as the streak: SYSTEM is neutral, QUIT or a past day without a
 * record is missed. Days before the first record are not tracked, so a new
 * user does not see weeks of misses.
 */
fun dayMark(date: LocalDate, days: Map<LocalDate, Outcome>, today: LocalDate): DayMark {
    val first = days.keys.minOrNull() ?: return DayMark.NONE
    if (date < first || date > today) return DayMark.NONE
    return when (days[date]) {
        Outcome.COMPLETED -> DayMark.DONE
        Outcome.SYSTEM -> DayMark.NONE
        Outcome.QUIT -> DayMark.MISSED
        null -> if (date == today) DayMark.NONE else DayMark.MISSED
    }
}

/** "[done] of [tracked] mornings this month"; tracked = done + missed. */
data class MonthStats(val done: Int, val tracked: Int)

fun monthStats(month: YearMonth, days: Map<LocalDate, Outcome>, today: LocalDate): MonthStats {
    val marks = (1..month.lengthOfMonth()).map { dayMark(month.atDay(it), days, today) }
    val done = marks.count { it == DayMark.DONE }
    return MonthStats(done, done + marks.count { it == DayMark.MISSED })
}
