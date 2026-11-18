package dev.thirtymin.app

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import java.time.LocalDate

/**
 * Persists the two facts [phase] needs, so they survive process death.
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

    private fun bootCount() = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, 0)

    private companion object {
        const val KEY_LAST_START = "last_start_epoch_day"
        const val KEY_ACTIVE_UNTIL = "active_until_elapsed"
        const val KEY_BOOT = "boot_count"
    }
}
