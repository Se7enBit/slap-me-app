package com.noikonomist.slapme.engine

import com.noikonomist.slapme.engine.EngineEffect.Slap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakTest {

    private val h = EngineHarness()

    @Test
    fun countingAddsToTheStreak() {
        h.use(3.minutes)
        assertEquals(3.minutes, h.state.streak)
    }

    @Test
    fun idleShorterThanTheBreakKeepsTheStreak() {
        h.use(3.minutes)
        h.idle(59.seconds)
        assertEquals(3.minutes, h.state.streak)

        h.use(1.minutes)
        assertEquals(4.minutes, h.state.streak)
    }

    @Test
    fun idleForTheBreakResetsTheStreak() {
        h.use(3.minutes)
        h.idle(60.seconds)
        assertEquals(0.seconds, h.state.streak)
    }

    @Test
    fun launcherCountsAsIdle() {
        h.use(3.minutes)
        h.hold(HOME, 60.seconds)
        assertEquals(0.seconds, h.state.streak)
    }

    @Test
    fun countingInterruptsTheIdleRun() {
        h.use(3.minutes)
        h.idle(40.seconds)
        h.use(5.seconds)
        h.idle(40.seconds)
        assertEquals(3.minutes + 5.seconds, h.state.streak)
    }

    @Test
    fun callDuringAStreakFreezesItAndUsageResumesWhereItWas() {
        h.use(8.minutes)
        val during = h.hold(IN_CALL, 20.minutes)
        assertTrue(during.ofType<Slap>().isEmpty())
        assertEquals(8.minutes, h.state.streak)

        assertTrue(h.use(1.minutes + 55.seconds).ofType<Slap>().isEmpty())
        assertEquals(1, h.use(5.seconds).ofType<Slap>().size)
    }

    @Test
    fun navigationDuringAStreakFreezesIt() {
        h.use(8.minutes)
        h.hold(NAVIGATING, 30.minutes)
        assertEquals(8.minutes, h.state.streak)
    }

    @Test
    fun idleRunContinuesAcrossAPause() {
        h.use(3.minutes)
        h.idle(40.seconds)
        h.hold(IN_CALL, 5.minutes)
        assertEquals("pause doesn't reset the streak", 3.minutes, h.state.streak)
        h.idle(20.seconds)
        assertEquals("40 s + 20 s idle is a full break", 0.seconds, h.state.streak)
    }

    @Test
    fun slapEndsTheStreak() {
        h.use(10.minutes)
        assertEquals(0.seconds, h.state.streak)
    }
}
