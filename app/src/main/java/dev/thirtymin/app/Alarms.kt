package dev.thirtymin.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Exact alarm at window start -> [AlarmReceiver] -> [BlockerService] armed.
 * The schedule rides in the Intent extras so debug and daily alarms can
 * carry different windows through the same receiver and service.
 */
object Alarms {
    // ponytail: hard-coded until the Phase 5 settings screen stores one.
    val DAILY = Schedule(LocalTime.of(6, 0), LocalTime.of(10, 0), 30)

    const val ACTION_DAILY = "dev.thirtymin.app.ALARM_DAILY"
    const val ACTION_DEBUG = "dev.thirtymin.app.ALARM_DEBUG"
    private const val TAG = "Alarms"

    fun scheduleDaily(context: Context): Boolean =
        set(context, ACTION_DAILY, DAILY, nextAlarmAt(ZonedDateTime.now(), DAILY))

    /** Debug builds only: a window that opens in 1 minute and lasts 3 (short, to test the timeout). */
    fun scheduleDebug(context: Context): Boolean {
        val at = ZonedDateTime.now().plusMinutes(1).withNano(0)
        val start = at.toLocalTime()
        // Clamp instead of wrapping past midnight (Schedule needs start < end).
        val end = start.plusMinutes(3).let { if (it > start) it else LocalTime.MAX }
        return set(context, ACTION_DEBUG, Schedule(start, end, 1), at)
    }

    /** Returns false (fail open, no auto block) when exact alarms are not allowed. */
    private fun set(
        context: Context,
        action: String,
        schedule: Schedule,
        at: ZonedDateTime
    ): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        if (!am.canScheduleExactAlarms()) return false
        // Identity = action + requestCode (extras are ignored by PendingIntent matching),
        // so the debug alarm never replaces or cancels the daily one.
        val pi =
            PendingIntent.getBroadcast(
                context,
                if (action == ACTION_DEBUG) 1 else 0,
                Intent(context, AlarmReceiver::class.java).setAction(action).putSchedule(schedule),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        return try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pi)
            Log.i(TAG, "$action set for $at")
            true
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
            Log.w(TAG, "exact alarm denied", e)
            false
        }
    }
}

private const val EXTRA_START = "window_start_sec"
private const val EXTRA_END = "window_end_sec"
private const val EXTRA_BLOCK_MINUTES = "block_minutes"

fun Intent.putSchedule(s: Schedule): Intent = putExtra(EXTRA_START, s.start.toSecondOfDay())
    .putExtra(EXTRA_END, s.end.toSecondOfDay())
    .putExtra(EXTRA_BLOCK_MINUTES, s.minutes)

/** Null if extras are missing or invalid; callers then do nothing (fail open). */
fun Intent.schedule(): Schedule? = runCatching {
    Schedule(
        LocalTime.ofSecondOfDay(getIntExtra(EXTRA_START, -1).toLong()),
        LocalTime.ofSecondOfDay(getIntExtra(EXTRA_END, -1).toLong()),
        getIntExtra(EXTRA_BLOCK_MINUTES, -1)
    )
}.getOrNull()

/**
 * One receiver for our own alarms and for the system events that wipe or
 * shift them (reboot, app update, clock/zone change, permission granted).
 * Every path is idempotent: scheduleDaily replaces the one daily alarm
 * (same PendingIntent identity), and arming re-derives phase() from disk,
 * so repeats, or events during a running block, change nothing.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reschedule = intent.action in RESCHEDULE_ACTIONS
        // Re-arm the next daily alarm first, so a failure below cannot skip
        // future days. Re-checks the exact alarm permission each time.
        if (intent.action == Alarms.ACTION_DAILY || reschedule) Alarms.scheduleDaily(context)
        // System events carry no schedule; they stand for the daily one.
        val schedule = if (reschedule) Alarms.DAILY else intent.schedule() ?: return

        // Stale (delivered after window end) or already blocked today: stay quiet.
        val store = SessionStore(context)
        val p =
            phase(
                LocalDateTime.now(),
                SystemClock.elapsedRealtime(),
                schedule,
                store.lastStart,
                store.activeUntil
            )
        // ACTIVE (block running: never restart or interrupt it), DONE, IDLE: stop here.
        if (p != Phase.ARMED) {
            Log.i(TAG, "${intent.action}: ignored, phase=$p")
            return
        }
        Log.i(TAG, "${intent.action}: arming")
        try {
            // Background FGS start is allowed right after an exact alarm and
            // after each RESCHEDULE_ACTIONS broadcast (documented exemptions).
            context.startForegroundService(
                Intent(context, BlockerService::class.java)
                    .setAction(BlockerService.ACTION_ARM)
                    .putSchedule(schedule)
            )
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: fail open, no block today.
            Log.w(TAG, "could not arm", e)
        }
    }

    private companion object {
        const val TAG = "AlarmReceiver"
        val RESCHEDULE_ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED, // alarms are wiped by reboot
                Intent.ACTION_MY_PACKAGE_REPLACED, // app updated
                Intent.ACTION_TIMEZONE_CHANGED, // RTC alarm is an absolute instant: 06:00 moved
                Intent.ACTION_TIME_CHANGED, // "android.intent.action.TIME_SET": clock set by hand
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED // granted
            )
    }
}
