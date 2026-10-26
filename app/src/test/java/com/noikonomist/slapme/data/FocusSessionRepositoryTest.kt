package com.noikonomist.slapme.data

import android.app.Application
import com.noikonomist.slapme.engine.FocusSession
import com.noikonomist.slapme.engine.MONDAY_10AM
import com.noikonomist.slapme.engine.MutableClock
import com.noikonomist.slapme.engine.TEST_ZONE
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FocusSessionRepositoryTest {

    private val db = inMemoryDatabase()
    private val clock = MutableClock(MONDAY_10AM, TEST_ZONE)
    private val repo = FocusSessionRepository(db.focusSessionDao(), clock)
    private val t0 = MONDAY_10AM.toEpochMilli()

    @After
    fun close() = db.close()

    @Test
    fun plannedSessionIsRunningUntilStopped() = runTest {
        val session = repo.start(25.minutes)

        assertEquals(FocusSession(session.id, t0, t0 + 25.minutes.inWholeMilliseconds), session)
        assertEquals(session, repo.running.first())
    }

    @Test
    fun openEndedSessionHasNoPlannedEnd() = runTest {
        assertNull(repo.start(null).plannedEndMillis)
    }

    @Test
    fun stopEndsTheRunningSessionNow() = runTest {
        val session = repo.start(null)
        clock.advance(12.minutes)

        assertTrue(repo.stop())
        assertNull(repo.running.first())
        assertEquals(t0 + 12.minutes.inWholeMilliseconds, db.focusSessionDao().get(session.id)!!.actualEnd)
    }

    @Test
    fun stopWithoutASessionDoesNothing() = runTest {
        assertFalse(repo.stop())
    }

    @Test
    fun startingANewSessionEndsThePreviousOne() = runTest {
        val first = repo.start(null)
        clock.advance(5.minutes)
        val second = repo.start(30.minutes)

        assertEquals(t0 + 5.minutes.inWholeMilliseconds, db.focusSessionDao().get(first.id)!!.actualEnd)
        assertEquals(second, repo.running.first())
    }

    @Test
    fun expiredSessionEndsAtItsPlannedEnd() = runTest {
        val session = repo.start(25.minutes)
        clock.advance(26.minutes)
        repo.markExpired(session.id)

        assertNull(repo.running.first())
        assertEquals(session.plannedEndMillis, db.focusSessionDao().get(session.id)!!.actualEnd)
    }
}
