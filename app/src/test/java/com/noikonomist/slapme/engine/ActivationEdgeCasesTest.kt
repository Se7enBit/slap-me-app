package com.noikonomist.slapme.engine

import com.noikonomist.slapme.engine.EngineEffect.CancelWarning
import com.noikonomist.slapme.engine.EngineEffect.DayRolledOver
import com.noikonomist.slapme.engine.EngineEffect.SessionExpired
import com.noikonomist.slapme.engine.EngineEffect.Slap
import com.noikonomist.slapme.engine.EngineEffect.Warn
import java.time.DayOfWeek.MONDAY
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationEdgeCasesTest {

    private fun block(id: Long, start: Int, end: Int, mask: Int = DaysMask.ALL) =
        ScheduleBlock(id, mask, startMinute = start, endMinute = end)

    @Test
    fun scheduleEndingMidStreakCancelsTheWarningAndNeverSlaps() {
        val h = EngineHarness(
            rules = ActivationRules(blocks = listOf(block(1, 9 * 60, 10 * 60))),
            start = instantAt("2026-10-26", "09:50:00"),
        )
        val effects = h.use(12.minutes)

        assertEquals(1, effects.ofType<Warn>().size)
        assertEquals(instantAt("2026-10-26", "10:00:00"), h.timeOf { it is CancelWarning })
        assertTrue(effects.ofType<Slap>().isEmpty())
        assertEquals("time outside the schedule is a break", 0.seconds, h.state.streak)
    }

    @Test
    fun nextBlockStartsWithAFreshStreak() {
        val h = EngineHarness(
            rules = ActivationRules(blocks = listOf(block(1, 9 * 60, 10 * 60), block(2, 11 * 60, 12 * 60))),
            start = instantAt("2026-10-26", "09:52:00"),
        )
        h.use(8.minutes) // 8 min streak when the first block ends
        h.use(1.hours) // used straight through the gap until 11:00

        assertTrue(h.use(9.minutes + 55.seconds).ofType<Slap>().isEmpty())
        assertEquals(1, h.use(5.seconds).ofType<Slap>().size)
    }

    @Test
    fun usageBeforeASessionStartsDoesNotCount() {
        val h = EngineHarness(rules = ActivationRules())
        assertTrue(h.use(30.minutes).ofType<Slap>().isEmpty())

        h.rules = ActivationRules(session = FocusSession(1, h.now.toEpochMilli(), plannedEndMillis = null))
        assertTrue(h.use(9.minutes + 55.seconds).ofType<Slap>().isEmpty())
        assertEquals(1, h.use(5.seconds).ofType<Slap>().size)
    }

    @Test
    fun plannedSessionExpiresOnceAndStopsCounting() {
        val session = FocusSession(
            id = 4,
            startMillis = MONDAY_10AM.toEpochMilli(),
            plannedEndMillis = MONDAY_10AM.plusSeconds(8 * 60).toEpochMilli(),
        )
        val h = EngineHarness(rules = ActivationRules(session = session))

        val effects = h.use(20.minutes)
        assertEquals(listOf(SessionExpired(4)), effects.ofType<SessionExpired>())
        assertEquals(MONDAY_10AM.plusSeconds(8 * 60), h.timeOf { it is SessionExpired })
        assertTrue(effects.ofType<Slap>().isEmpty())
        assertFalse(h.engine.isArmed())
    }

    @Test
    fun blockCrossingMidnightKeepsCountingIntoTheNextDay() {
        val h = EngineHarness(
            rules = ActivationRules(blocks = listOf(block(1, 23 * 60 + 30, 30, mask = DaysMask.of(MONDAY)))),
            start = instantAt("2026-10-26", "23:55:00"),
        )
        val effects = h.use(10.minutes)

        assertEquals(1, effects.ofType<DayRolledOver>().size)
        assertEquals(instantAt("2026-10-27", "00:05:00"), h.timeOf { it is Slap })
    }

    @Test
    fun focusStretchEndsWhenActiveTimeEnds() {
        val h = EngineHarness(
            rules = ActivationRules(blocks = listOf(block(1, 9 * 60, 10 * 60))),
            start = instantAt("2026-10-26", "09:55:00"),
        )
        h.use(4.minutes)
        h.idle(2.minutes) // 09:59 -> 10:01, the block ends at 10:00
        assertEquals(0L, h.state.focusRunMillis)
        assertEquals(5.minutes.inWholeMilliseconds, h.state.today!!.longestFocusMillis)
    }

    @Test
    fun screenOffOvernightAfterTheBlockIsNotFocusTime() {
        val h = EngineHarness(
            rules = ActivationRules(blocks = listOf(block(1, 9 * 60, 17 * 60))),
            start = instantAt("2026-10-26", "16:58:00"),
        )
        h.use(1.minutes)
        val rollover = h.idle(16.hours).ofType<DayRolledOver>().single() // 16:59 -> 08:59 next day

        assertEquals(2.minutes.inWholeMilliseconds, rollover.stats.longestFocusMillis)
        assertEquals(0L, h.state.today!!.longestFocusMillis)
    }
}
