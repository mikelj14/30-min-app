package dev.thirtymin.app

import dev.thirtymin.app.Outcome.COMPLETED
import dev.thirtymin.app.Outcome.QUIT
import dev.thirtymin.app.Outcome.SYSTEM
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class StreaksTest {
    private val today = LocalDate.of(2026, 11, 19)

    /** Outcomes listed oldest first, ending today. null = no record that day. */
    private fun days(vararg o: Outcome?): Map<LocalDate, Outcome> = o.withIndex()
        .filter { it.value != null }
        .associate { today.minusDays((o.size - 1 - it.index).toLong()) to it.value!! }

    @Test fun noHistoryIsZero() {
        assertEquals(0, currentStreak(emptyMap(), today))
        assertEquals(0, longestStreak(emptyMap()))
    }

    @Test fun completedDaysCount() =
        assertEquals(3, currentStreak(days(COMPLETED, COMPLETED, COMPLETED), today))

    @Test fun todayNotYetDoneKeepsStreak() =
        assertEquals(2, currentStreak(days(COMPLETED, COMPLETED, null), today))

    @Test fun quitTodayBreaks() = assertEquals(
        0,
        currentStreak(days(COMPLETED, COMPLETED, QUIT), today)
    )

    @Test fun missedDayBreaks() = assertEquals(
        1,
        currentStreak(days(COMPLETED, null, COMPLETED), today)
    )

    @Test fun systemDayIsNeutral() =
        assertEquals(2, currentStreak(days(COMPLETED, SYSTEM, COMPLETED), today))

    @Test fun runningBlockTodayIsNeutral() =
        assertEquals(2, currentStreak(days(COMPLETED, COMPLETED, SYSTEM), today))

    @Test fun longestFindsBestRun() = assertEquals(
        3,
        longestStreak(
            days(COMPLETED, COMPLETED, SYSTEM, COMPLETED, QUIT, COMPLETED, null, COMPLETED)
        )
    )

    @Test fun longestSurvivesMissedGapAtEnd() =
        assertEquals(2, longestStreak(days(COMPLETED, COMPLETED, null, null)))

    @Test fun dayMarks() {
        val d = days(COMPLETED, QUIT, SYSTEM, null, null) // 15th..19th, today pending
        fun mark(day: Int) = dayMark(LocalDate.of(2026, 11, day), d, today)
        assertEquals(DayMark.NONE, mark(14)) // before first record: untracked
        assertEquals(DayMark.DONE, mark(15))
        assertEquals(DayMark.MISSED, mark(16)) // quit
        assertEquals(DayMark.NONE, mark(17)) // system: neutral
        assertEquals(DayMark.MISSED, mark(18)) // no record, past
        assertEquals(DayMark.NONE, mark(19)) // today, pending
        assertEquals(DayMark.NONE, mark(20)) // future
    }

    @Test fun noHistoryMarksNothing() =
        assertEquals(DayMark.NONE, dayMark(today.minusDays(1), emptyMap(), today))

    @Test fun monthCountsOnlyTrackedDays() {
        val d = days(COMPLETED, QUIT, SYSTEM, null, COMPLETED) // 15th..19th
        assertEquals(
            MonthStats(done = 2, tracked = 4),
            monthStats(YearMonth.of(2026, 11), d, today)
        )
        assertEquals(MonthStats(0, 0), monthStats(YearMonth.of(2026, 10), d, today))
    }
}
