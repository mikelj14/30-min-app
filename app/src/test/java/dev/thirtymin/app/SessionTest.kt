package dev.thirtymin.app

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
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

    @Test fun armedAndInUseBlocks() = assertEquals(
        ArmAction.BLOCK,
        armAction(Phase.ARMED, interactive = true, keyguardLocked = false)
    )

    @Test fun armedButScreenOffOrLockedWaits() {
        assertEquals(
            ArmAction.WAIT,
            armAction(Phase.ARMED, interactive = false, keyguardLocked = true)
        )
        assertEquals(
            ArmAction.WAIT,
            armAction(Phase.ARMED, interactive = true, keyguardLocked = true)
        )
        // Screen off but keyguard not yet engaged (lock delay): not "in use".
        assertEquals(
            ArmAction.WAIT,
            armAction(Phase.ARMED, interactive = false, keyguardLocked = false)
        )
    }

    @Test fun notArmedStopsEvenWhenInUse() = Phase.entries.filter { it != Phase.ARMED }.forEach {
        assertEquals(ArmAction.STOP, armAction(it, interactive = true, keyguardLocked = false))
    }

    @Test fun staleAlarmAfterWindowStops() {
        val p = phaseAt(at(9, 30)) // alarm delivered late, window 06:00-09:00 over
        assertEquals(ArmAction.STOP, armAction(p, interactive = true, keyguardLocked = false))
    }

    @Test fun nextAlarmUsesCalendarNotPlus24hAcrossDst() {
        // Europe/London springs forward 2026-03-29 01:00 -> 02:00: that day is 23h long.
        val london = ZoneId.of("Europe/London")
        val now = LocalDate.of(2026, 3, 28).atTime(6, 0).atZone(london)
        val next = nextAlarmAt(now, schedule)
        assertEquals(LocalDate.of(2026, 3, 29).atTime(6, 0), next.toLocalDateTime())
        assertEquals(Duration.ofHours(23), Duration.between(now, next))
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

    @Test(expected = IllegalArgumentException::class)
    fun windowShorterThanBlockRejected() {
        Schedule(LocalTime.of(6, 0), LocalTime.of(6, 20), 30)
    }

    @Test fun windowEqualToBlockAllowed() =
        assertEquals(30, Schedule(LocalTime.of(6, 0), LocalTime.of(6, 30), 30).minutes)

    @Test fun userBlockLimits() {
        val (s, e) = LocalTime.of(6, 0) to LocalTime.of(10, 0)
        assertEquals(5, userSchedule(s, e, 5).minutes)
        assertEquals(120, userSchedule(s, e, 120).minutes)
        for (bad in listOf(0, 4, 121)) {
            val err = runCatching { userSchedule(s, e, bad) }.exceptionOrNull()
            assertEquals("minutes=$bad", "block must be 5-120 minutes", err?.message)
        }
    }

    private val dialer = setOf("com.dialer")

    @Test fun passStaysHiddenDuringCall() =
        assertEquals(false, passReturns(true, "com.launcher", dialer, 99_000, 30_000))

    @Test fun passReturnsWhenUserLeavesDialer() =
        assertEquals(true, passReturns(false, "com.launcher", dialer, 1_000, 30_000))

    @Test fun passWaitsInDialerUntilTimeUp() {
        assertEquals(false, passReturns(false, "com.dialer", dialer, 29_999, 30_000))
        assertEquals(true, passReturns(false, "com.dialer", dialer, 30_000, 30_000))
    }

    @Test fun passWithoutUsageAccessIsTimed() {
        assertEquals(false, passReturns(false, null, dialer, 1_000, 30_000))
        assertEquals(true, passReturns(false, null, dialer, 30_000, 30_000))
    }

    @Test fun alarmLostOnlyAfterForceStop() {
        assertEquals(true, alarmLost(true, setBoot = 61, boot = 61, pending = false))
        assertEquals(false, alarmLost(true, setBoot = 61, boot = 61, pending = true))
        assertEquals(false, alarmLost(true, setBoot = 61, boot = 62, pending = false)) // reboot
        // First run, then no permission:
        assertEquals(false, alarmLost(true, setBoot = null, boot = 61, pending = false))
        assertEquals(false, alarmLost(false, setBoot = 61, boot = 61, pending = false))
    }
}
