package com.noikonomist.slapme.data

import android.app.Application
import com.noikonomist.slapme.engine.DailyStats
import com.noikonomist.slapme.engine.EngineEffect
import com.noikonomist.slapme.engine.MONDAY_10AM
import com.noikonomist.slapme.engine.MutableClock
import com.noikonomist.slapme.engine.OverrideType
import com.noikonomist.slapme.engine.SlapReason
import com.noikonomist.slapme.engine.TEST_ZONE
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EffectRecorderTest {

    private val db = inMemoryDatabase()
    private val clock = MutableClock(MONDAY_10AM, TEST_ZONE)
    private val sessions = FocusSessionRepository(db.focusSessionDao(), clock)
    private val history = HistoryRepository(db.slapEventDao(), db.overrideEventDao(), db.dailyUsageDao())
    private val recorder = EffectRecorder(db.slapEventDao(), db.overrideEventDao(), db.dailyUsageDao(), db.focusSessionDao())

    private val t0 = MONDAY_10AM.toEpochMilli()
    private val monday = LocalDate.parse("2026-10-26")

    @After
    fun close() = db.close()

    @Test
    fun slapsAndOverridesAreLoggedAtTheGivenTime() = runTest {
        recorder.record(
            listOf(
                EngineEffect.Warn(t0 + 60_000, freeSnoozeAvailable = true),
                EngineEffect.Slap(SlapReason.THRESHOLD, level = 1, packageName = "com.example.feed"),
                EngineEffect.LogOverride(OverrideType.SNOOZE, 5.minutes),
                EngineEffect.LogOverride(OverrideType.FRICTION, 30.seconds),
                EngineEffect.CancelWarning,
            ),
            atMillis = t0,
        )

        assertEquals(
            listOf(SlapRecord(t0, "com.example.feed", 1, SlapReason.THRESHOLD)),
            history.slaps(t0, t0 + 1).first(),
        )
        assertEquals(
            listOf(
                OverrideRecord(t0, OverrideType.SNOOZE, 5.minutes),
                OverrideRecord(t0, OverrideType.FRICTION, 30.seconds),
            ),
            history.overrides(t0, t0 + 1).first(),
        )
    }

    @Test
    fun rangesIncludeTheStartAndExcludeTheEnd() = runTest {
        recorder.record(listOf(EngineEffect.Slap(SlapReason.LOCKOUT, 2, null)), atMillis = t0 - 1)
        recorder.record(listOf(EngineEffect.Slap(SlapReason.LOCKOUT, 2, null)), atMillis = t0)
        recorder.record(listOf(EngineEffect.Slap(SlapReason.DAILY_CAP, 0, null)), atMillis = t0 + 1_000)

        assertEquals(listOf(t0), history.slaps(t0, t0 + 1_000).first().map { it.timestampMillis })
    }

    @Test
    fun dayRolloverStoresTheDailyUsageInSeconds() = runTest {
        recorder.record(
            listOf(EngineEffect.DayRolledOver(DailyStats(monday, countedMillis = 3_600_999, longestFocusMillis = 1_200_500))),
            atMillis = t0,
        )
        assertEquals(
            listOf(DailyStats(monday, countedMillis = 3_600_000, longestFocusMillis = 1_200_000)),
            history.dailyUsage(monday, monday).first(),
        )
    }

    @Test
    fun recordTodayOverwritesTheSameDay() = runTest {
        recorder.recordToday(DailyStats(monday, 60_000, 60_000))
        recorder.recordToday(DailyStats(monday, 120_000, 90_000))
        recorder.recordToday(DailyStats(monday.plusDays(1), 5_000, 5_000))

        assertEquals(
            listOf(DailyStats(monday, 120_000, 90_000), DailyStats(monday.plusDays(1), 5_000, 5_000)),
            history.dailyUsage(monday, monday.plusDays(6)).first(),
        )
    }

    @Test
    fun expiredSessionIsEndedAtItsPlannedEnd() = runTest {
        val session = sessions.start(25.minutes)
        recorder.record(listOf(EngineEffect.SessionExpired(session.id)), atMillis = t0 + 26.minutes.inWholeMilliseconds)

        assertNull(sessions.running.first())
        assertEquals(session.plannedEndMillis, db.focusSessionDao().get(session.id)!!.actualEnd)
    }
}
