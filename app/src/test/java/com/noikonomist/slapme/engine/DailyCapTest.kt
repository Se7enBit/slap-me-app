package com.noikonomist.slapme.engine

import com.noikonomist.slapme.engine.EngineEffect.DayRolledOver
import com.noikonomist.slapme.engine.EngineEffect.Slap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCapTest {

    private val capped = EngineConfig(dailyCap = 30.minutes)

    /** Three 9-minute streaks separated by breaks: 27 min counted, no slaps. */
    private fun EngineHarness.useJustUnderTheCap() {
        repeat(3) {
            assertTrue(use(9.minutes).ofType<Slap>().isEmpty())
            idle(60.seconds)
        }
        assertEquals(27.minutes.inWholeMilliseconds, state.today!!.countedMillis)
    }

    @Test
    fun overTheCapTheThresholdDropsToTheShortestLevel() {
        val h = EngineHarness(config = capped)
        h.useJustUnderTheCap()

        assertTrue("29 min counted, still under the cap", h.use(2.minutes).ofType<Slap>().isEmpty())
        assertEquals(
            listOf(Slap(SlapReason.DAILY_CAP, level = 0, packageName = FEED)),
            h.use(1.minutes).ofType<Slap>(),
        )
        assertEquals(1, h.state.level)
    }

    @Test
    fun capStaysInForceForTheRestOfTheDayEvenAfterEscalationResets() {
        val h = EngineHarness(config = capped)
        h.useJustUnderTheCap()
        h.use(3.minutes)
        h.idle(10.minutes)
        assertEquals(0, h.state.level)

        assertTrue(h.use(1.minutes + 55.seconds).ofType<Slap>().isEmpty())
        assertEquals(SlapReason.DAILY_CAP, h.use(5.seconds).ofType<Slap>().single().reason)
    }

    @Test
    fun onlyActiveCountingTimeCountsTowardTheCap() {
        val schedule = ActivationRules(
            blocks = listOf(ScheduleBlock(1, DaysMask.ALL, startMinute = 9 * 60, endMinute = 17 * 60)),
        )
        val h = EngineHarness(config = capped, rules = schedule, start = instantAt("2026-10-26", "07:00:00"))

        assertTrue(h.use(55.minutes).ofType<Slap>().isEmpty())
        h.hold(IN_CALL, 30.minutes)
        assertEquals(0L, h.state.today!!.countedMillis)
    }

    @Test
    fun capResetsAtMidnight() {
        val h = EngineHarness(config = capped, start = instantAt("2026-10-26", "23:00:00"))
        h.useJustUnderTheCap()

        val rollover = h.idle(40.minutes).ofType<DayRolledOver>().single()
        assertEquals(27.minutes.inWholeMilliseconds, rollover.stats.countedMillis)
        assertEquals(0L, h.state.today!!.countedMillis)
        assertTrue(h.use(5.minutes).ofType<Slap>().isEmpty())
    }

    @Test
    fun noCapMeansNormalThresholds() {
        val h = EngineHarness()
        repeat(6) {
            assertTrue(h.use(9.minutes).ofType<Slap>().isEmpty())
            h.idle(60.seconds)
        }
        assertEquals(54.minutes.inWholeMilliseconds, h.state.today!!.countedMillis)
    }
}
