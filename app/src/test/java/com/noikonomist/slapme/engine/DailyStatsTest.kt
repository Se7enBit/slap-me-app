package com.noikonomist.slapme.engine

import com.noikonomist.slapme.engine.EngineEffect.DayRolledOver
import java.time.LocalDate
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyStatsTest {

    private val h = EngineHarness()
    private val today get() = h.state.today!!

    @Test
    fun countedTimeIsOnlyCountingTime() {
        h.use(2.minutes)
        h.hold(IN_CALL, 3.minutes)
        h.idle(30.seconds)
        h.use(1.minutes)

        assertEquals(3.minutes.inWholeMilliseconds, today.countedMillis)
    }

    @Test
    fun focusStretchIncludesIdleAndPausedActiveTimeAndEndsAtASlap() {
        h.use(10.minutes) // slap
        assertEquals(10.minutes.inWholeMilliseconds, today.longestFocusMillis)
        assertEquals(0L, h.state.focusRunMillis)

        h.use(4.minutes)
        h.idle(60.seconds)
        h.use(4.minutes) // level 1 = 5 min, but the break reset the streak
        h.hold(IN_CALL, 2.minutes)
        assertEquals(11.minutes.inWholeMilliseconds, h.state.focusRunMillis)
        assertEquals(11.minutes.inWholeMilliseconds, today.longestFocusMillis)
    }

    @Test
    fun dayRollsOverAtLocalMidnightSplittingTheInterval() {
        val h = EngineHarness(start = instantAt("2026-10-26", "23:58:02"))
        val rollover = h.use(4.minutes).ofType<DayRolledOver>().single()

        assertEquals(LocalDate.parse("2026-10-26"), rollover.stats.date)
        assertEquals((1.minutes + 58.seconds).inWholeMilliseconds, rollover.stats.countedMillis)
        assertEquals(LocalDate.parse("2026-10-27"), h.state.today!!.date)
        assertEquals((2.minutes + 2.seconds).inWholeMilliseconds, h.state.today!!.countedMillis)
    }

    @Test
    fun everyDayOfALongGapRollsOver() {
        h.use(1.minutes)
        val rollovers = h.idle(2.days).ofType<DayRolledOver>()

        assertEquals(
            listOf(LocalDate.parse("2026-10-26"), LocalDate.parse("2026-10-27")),
            rollovers.map { it.stats.date },
        )
        assertEquals(LocalDate.parse("2026-10-28"), today.date)
    }

    @Test
    fun usedFreeSnoozesArePrunedAtMidnight() {
        h.snooze()
        h.idle(1.days)
        assertEquals(emptySet<String>(), h.state.usedSnoozePeriods)
    }
}
