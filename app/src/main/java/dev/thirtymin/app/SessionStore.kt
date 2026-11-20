package dev.thirtymin.app

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import java.time.LocalDate
import java.time.LocalTime

/**
 * Persists the facts [phase] needs and the user's [Schedule], so they survive
 * process death, reboot and app updates.
 * Lives in /data/data/dev.thirtymin.app/shared_prefs/session.xml.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val resolver = context.contentResolver

    /** Day the last block started, or null if never. */
    val lastStart: LocalDate?
        get() = prefs.getLong(KEY_LAST_START, -1).takeIf { it >= 0 }?.let(LocalDate::ofEpochDay)

    /**
     * `elapsedRealtime` end of the running block, or null.
     * elapsedRealtime restarts at 0 on reboot, so a value from an earlier boot
     * is meaningless: ignore it (reboot ends the block, fail open).
     */
    val activeUntil: Long?
        get() =
            prefs
                .getLong(KEY_ACTIVE_UNTIL, -1)
                .takeIf { it >= 0 && prefs.getInt(KEY_BOOT, -1) == bootCount() }

    /** commit = true: written to disk before returning (apply() is async and can be lost on kill). */
    fun startBlock(today: LocalDate, until: Long) = prefs.edit(commit = true) {
        putLong(KEY_LAST_START, today.toEpochDay())
        putLong(KEY_ACTIVE_UNTIL, until)
        putInt(KEY_BOOT, bootCount())
    }

    /** commit: a kill right after an early exit must not leave the block "running" on disk. */
    fun endBlock() = prefs.edit(commit = true) { remove(KEY_ACTIVE_UNTIL) }

    /** Saved settings, or [DEFAULT_SCHEDULE] if never saved or unreadable (fail safe, not crash). */
    val schedule: Schedule
        get() =
            runCatching {
                Schedule(
                    LocalTime.ofSecondOfDay(prefs.getInt(KEY_START, -1).toLong()),
                    LocalTime.ofSecondOfDay(prefs.getInt(KEY_END, -1).toLong()),
                    prefs.getInt(KEY_MINUTES, -1)
                )
            }.getOrDefault(DEFAULT_SCHEDULE)

    /** True while a block runs (same test as [phase] ACTIVE). */
    fun blockRunning(): Boolean = activeUntil?.let { SystemClock.elapsedRealtime() < it } == true

    /** Boot the daily alarm was last set in, or null if never set (first run). */
    val alarmSetBoot: Int? get() = prefs.getInt(KEY_ALARM_BOOT, -1).takeIf { it >= 0 }

    fun markAlarmSet() {
        if (alarmSetBoot != bootCount()) prefs.edit { putInt(KEY_ALARM_BOOT, bootCount()) }
    }

    /** Set when a force-stop deleted the alarm; the app shows a warning once. */
    fun noteAlarmLost() = prefs.edit { putBoolean(KEY_ALARM_LOST, true) }

    fun takeAlarmLost(): Boolean {
        val lost = prefs.getBoolean(KEY_ALARM_LOST, false)
        if (lost) prefs.edit { remove(KEY_ALARM_LOST) }
        return lost
    }

    fun saveSchedule(s: Schedule) = prefs.edit(commit = true) {
        putInt(KEY_START, s.start.toSecondOfDay())
        putInt(KEY_END, s.end.toSecondOfDay())
        putInt(KEY_MINUTES, s.minutes)
    }

    /** The how-it-works pages were seen (finished or skipped) once. */
    var tutorialSeen: Boolean
        get() = prefs.getBoolean(KEY_TUTORIAL_SEEN, false)
        set(v) = prefs.edit { putBoolean(KEY_TUTORIAL_SEEN, v) }

    fun bootCount() = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, 0)

    companion object {
        val DEFAULT_SCHEDULE = Schedule(LocalTime.of(6, 0), LocalTime.of(10, 0), 30)
        private const val KEY_ALARM_BOOT = "alarm_set_boot"
        private const val KEY_ALARM_LOST = "alarm_lost"
        private const val KEY_START = "window_start_sec"
        private const val KEY_END = "window_end_sec"
        private const val KEY_MINUTES = "block_minutes"
        private const val KEY_LAST_START = "last_start_epoch_day"
        private const val KEY_ACTIVE_UNTIL = "active_until_elapsed"
        private const val KEY_BOOT = "boot_count"
        private const val KEY_TUTORIAL_SEEN = "tutorial_seen"
    }
}
