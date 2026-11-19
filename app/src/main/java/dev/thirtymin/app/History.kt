package dev.thirtymin.app

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Session history: what happened on past mornings. Read by the History screen
// only. Blocking decisions never read it; they use SessionStore (SharedPreferences).

/**
 * One morning. [day] is the local date at block start (epoch day, same value
 * as SessionStore's last_start), and the primary key: one row per day.
 * [outcome] null = still running, or the process died before the end (SYSTEM).
 */
@Entity(tableName = "session")
data class SessionRecord(
    @PrimaryKey val day: Long,
    // Wall clock ms, for "6:42 - 7:12".
    val startedAt: Long,
    val plannedMinutes: Int,
    val endedAt: Long? = null,
    // Stored as its name, e.g. "COMPLETED".
    val outcome: Outcome? = null
)

@Dao
interface SessionDao {
    /** IGNORE: a second start on the same day keeps the first row. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun start(record: SessionRecord)

    /** Only an open session can end, so a second end never overwrites the first. */
    @Query(
        "UPDATE session SET outcome = :outcome, endedAt = :endedAt " +
            "WHERE day = :day AND outcome IS NULL"
    )
    suspend fun finish(day: Long, outcome: Outcome, endedAt: Long): Int

    /** Newest first. The Flow emits again on every change to the table. */
    @Query("SELECT * FROM session ORDER BY day DESC")
    fun all(): Flow<List<SessionRecord>>
}

/** day -> outcome; a row without an outcome counts as SYSTEM (running or process died). */
fun List<SessionRecord>.outcomeMap(): Map<LocalDate, Outcome> =
    associate { LocalDate.ofEpochDay(it.day) to (it.outcome ?: Outcome.SYSTEM) }

/** All rows; a read failure (corrupt file, enum renamed) gives empty history, not a crash. */
fun SessionDao.safeAll(): Flow<List<SessionRecord>> = all().catch {
    Log.e("History", "history read failed", it)
    emit(emptyList())
}

fun SessionDao.outcomes(): Flow<Map<LocalDate, Outcome>> = safeAll().map { it.outcomeMap() }

/**
 * Bump [version] and add a Migration on every schema change. No
 * fallbackToDestructiveMigration: a missing migration crashes the history
 * write (caught in [record]) instead of silently deleting the user's history.
 */
@Database(entities = [SessionRecord::class], version = 1)
abstract class HistoryDb : RoomDatabase() {
    abstract fun sessions(): SessionDao

    companion object {
        @Volatile private var instance: HistoryDb? = null

        fun get(context: Context): HistoryDb = instance ?: synchronized(this) {
            instance ?: Room
                .databaseBuilder(context.applicationContext, HistoryDb::class.java, "history.db")
                .build()
                .also { instance = it }
        }

        // Process-wide and never cancelled, so a write started in a service's
        // onDestroy still finishes. One thread: writes run in the order sent
        // (start before finish).
        private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

        /**
         * Fire-and-forget write off the main thread. Any failure is logged and
         * dropped: history must never block, delay or crash the blocker.
         */
        fun record(context: Context, write: suspend SessionDao.() -> Unit) {
            val app = context.applicationContext
            writes.launch {
                runCatching { get(app).sessions().write() }
                    .onFailure { Log.e("History", "history write failed", it) }
            }
        }
    }
}
