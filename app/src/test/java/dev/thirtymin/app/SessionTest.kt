package dev.thirtymin.app

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private val schedule = Schedule(LocalTime.of(6, 0), LocalTime.of(9, 0), 30)
    private val day = LocalDate.of(2026, 11, 18)

    private fun at(h: Int, m: Int = 0) = day.atTime(h, m)

    private fun phaseAt(
        now: LocalDateTime,
        lastStart: LocalDate? = null,
        activeUntil: Long? = null,
        nowElapsed: Long = 1_000
    ) = phase(now, nowElapsed, schedule, lastStart, activeUntil)

    @Test fun beforeWindowIsIdle() = assertEquals(Phase.IDLE, phaseAt(at(5, 59)))

    @Test fun windowStartIsArmed() = assertEquals(Phase.ARMED, phaseAt(at(6)))

    @Test fun windowEndIsIdle() = assertEquals(Phase.IDLE, phaseAt(at(9)))

    @Test fun blockedTodayIsDone() = assertEquals(Phase.DONE, phaseAt(at(7), lastStart = day))

    @Test fun yesterdaysBlockDoesNotCount() =
        assertEquals(Phase.ARMED, phaseAt(at(7), lastStart = day.minusDays(1)))

    @Test fun runningBlockIsActive() =
        assertEquals(Phase.ACTIVE, phaseAt(at(7), lastStart = day, activeUntil = 2_000))

    @Test fun expiredBlockIsNotActive() =
        assertEquals(Phase.DONE, phaseAt(at(7), lastStart = day, activeUntil = 1_000))

    @Test fun onlyArmedStartsOnUnlock() {
        assertTrue(shouldStartOnUnlock(Phase.ARMED))
        Phase.entries.filter { it != Phase.ARMED }.forEach { assertFalse(shouldStartOnUnlock(it)) }
    }

    @Test fun nextAlarmTodayWhenBeforeStart() = assertEquals(
        at(6),
        nextWindowStart(at(5), schedule)
    )

    @Test fun nextAlarmTomorrowWhenAtOrPastStart() {
        assertEquals(at(6).plusDays(1), nextWindowStart(at(6), schedule))
        assertEquals(at(6).plusDays(1), nextWindowStart(at(23), schedule))
    }

    @Test(expected = IllegalArgumentException::class)
    fun backwardsWindowRejected() {
        Schedule(LocalTime.of(9, 0), LocalTime.of(6, 0), 30)
    }

    @Test fun beforeWindowEndIsArmed() = assertEquals(Phase.ARMED, phaseAt(at(8, 59)))
}
