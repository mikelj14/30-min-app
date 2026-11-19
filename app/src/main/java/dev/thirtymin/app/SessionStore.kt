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

    fun endBlock() = prefs.edit { remove(KEY_ACTIVE_UNTIL) }

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

    fun saveSchedule(s: Schedule) = prefs.edit(commit = true) {
        putInt(KEY_START, s.start.toSecondOfDay())
        putInt(KEY_END, s.end.toSecondOfDay())
        putInt(KEY_MINUTES, s.minutes)
    }

    private fun bootCount() = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, 0)

    companion object {
        val DEFAULT_SCHEDULE = Schedule(LocalTime.of(6, 0), LocalTime.of(10, 0), 30)
        private const val KEY_START = "window_start_sec"
        private const val KEY_END = "window_end_sec"
        private const val KEY_MINUTES = "block_minutes"
        private const val KEY_LAST_START = "last_start_epoch_day"
        private const val KEY_ACTIVE_UNTIL = "active_until_elapsed"
        private const val KEY_BOOT = "boot_count"
    }
}
