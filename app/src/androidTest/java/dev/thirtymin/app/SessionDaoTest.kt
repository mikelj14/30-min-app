package dev.thirtymin.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on the phone against real SQLite, in memory (app's history.db untouched). */
@RunWith(AndroidJUnit4::class)
class SessionDaoTest {
    private lateinit var db: HistoryDb
    private val dao get() = db.sessions()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HistoryDb::class.java
        ).build()
    }

    @After fun close() = db.close()

    @Test fun secondStartSameDayKeepsFirst() = runBlocking {
        dao.start(SessionRecord(day = 1, startedAt = 100, plannedMinutes = 30))
        dao.start(SessionRecord(day = 1, startedAt = 999, plannedMinutes = 5))
        assertEquals(listOf(SessionRecord(1, 100, 30)), dao.all().first())
    }

    @Test fun firstEndWinsSecondIsIgnored() = runBlocking {
        dao.start(SessionRecord(day = 1, startedAt = 100, plannedMinutes = 30))
        assertEquals(1, dao.finish(1, Outcome.COMPLETED, 200))
        assertEquals(0, dao.finish(1, Outcome.QUIT, 300))
        assertEquals(Outcome.COMPLETED, dao.all().first().single().outcome)
        assertEquals(200L, dao.all().first().single().endedAt)
    }

    @Test fun finishWithoutStartWritesNothing() = runBlocking {
        assertEquals(0, dao.finish(1, Outcome.COMPLETED, 200))
        assertEquals(emptyList<SessionRecord>(), dao.all().first())
    }

    @Test fun newestFirst() = runBlocking {
        dao.start(SessionRecord(day = 1, startedAt = 100, plannedMinutes = 30))
        dao.start(SessionRecord(day = 2, startedAt = 200, plannedMinutes = 30))
        assertEquals(listOf(2L, 1L), dao.all().first().map { it.day })
    }
}
