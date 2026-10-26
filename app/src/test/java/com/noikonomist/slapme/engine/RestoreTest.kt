package com.noikonomist.slapme.engine

import com.noikonomist.slapme.engine.EngineEffect.EndLockout
import com.noikonomist.slapme.engine.EngineEffect.ServiceGap
import com.noikonomist.slapme.engine.EngineEffect.Slap
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The monitor saves [EngineState] and hands it to a fresh engine after a process death or reboot. */
class RestoreTest {

    /** A new harness (new engine, new clock) continuing from [saved] after [downtime]. */
    private fun restore(before: EngineHarness, downtime: kotlin.time.Duration) =
        EngineHarness(
            config = before.config,
            rules = before.rules,
            start = before.now.plusMillis(downtime.inWholeMilliseconds),
            state = before.state.copy(),
        )

    @Test
    fun rebootMidLockoutKeepsTheLockout() {
        val before = EngineHarness()
        before.reachLockout()
        val until = before.state.lockoutUntilMillis

        val after = restore(before, downtime = 3.minutes)
        val effects = after.use(5.seconds)

        assertEquals(listOf(ServiceGap(3.minutes)), effects.ofType<ServiceGap>())
        assertEquals(SlapReason.LOCKOUT, effects.ofType<Slap>().single().reason)
        assertEquals(until, after.state.lockoutUntilMillis)
        assertEquals(2, after.state.level)
    }

    @Test
    fun rebootMidLockoutStillEndsOnTime() {
        val before = EngineHarness()
        before.reachLockout()

        val after = restore(before, downtime = 3.minutes)
        after.idle(11.minutes + 55.seconds)
        assertTrue(after.state.inLockout)
        assertEquals(listOf(EndLockout), after.idle(5.seconds).ofType<EndLockout>())
        assertEquals(0, after.state.level)
    }

    @Test
    fun restoreAfterTheLockoutAlreadyEndedClearsIt() {
        val before = EngineHarness()
        before.reachLockout()

        val after = restore(before, downtime = 20.minutes)
        assertEquals(listOf(EndLockout), after.observe(HOME).ofType<EndLockout>())
        assertNull(after.state.lockoutUntilMillis)
        assertEquals(0, after.state.level)
    }

    @Test
    fun quickRestartKeepsTheStreakAndLevel() {
        val before = EngineHarness()
        before.use(10.minutes) // level 1
        before.use(3.minutes)

        val after = restore(before, downtime = 5.seconds)
        assertTrue(after.use(1.minutes + 50.seconds).ofType<Slap>().isEmpty())
        assertEquals(listOf(1), after.use(5.seconds).ofType<Slap>().map { it.level })
    }

    @Test
    fun countingGapIsTreatedAsIdleAndReported() {
        val h = EngineHarness()
        h.use(8.minutes)
        h.silence(2.minutes)

        assertEquals(listOf(ServiceGap(2.minutes + 0.seconds)), h.observe(using()).ofType<ServiceGap>())
        assertEquals("a 2 min gap is a break", 0.seconds, h.state.streak)
        assertEquals(8.minutes.inWholeMilliseconds, h.state.today!!.countedMillis)
    }

    @Test
    fun shortGapsStillCount() {
        val h = EngineHarness()
        h.use(1.minutes)
        h.silence(20.seconds)

        assertTrue(h.observe(using()).ofType<ServiceGap>().isEmpty())
        assertEquals(1.minutes + 20.seconds, h.state.streak)
    }

    @Test
    fun longIdleIsNotAServiceGap() {
        val h = EngineHarness()
        h.use(1.minutes)
        h.observe(SCREEN_OFF)
        h.silence(3.hours)
        assertTrue(h.observe(using()).ofType<ServiceGap>().isEmpty())
    }

    @Test
    fun longCallIsNotAServiceGap() {
        val h = EngineHarness()
        h.use(4.minutes)
        h.observe(IN_CALL)
        h.silence(1.hours)
        assertTrue(h.observe(using()).ofType<ServiceGap>().isEmpty())
        assertEquals(4.minutes, h.state.streak)
    }

    @Test
    fun clockGoingBackwardsIsIgnored() {
        val h = EngineHarness()
        h.use(1.minutes)
        h.clock.advance((-10).minutes)

        assertTrue(h.observe(using()).isEmpty())
        assertEquals(1.minutes, h.state.streak)
        h.use(30.seconds)
        assertEquals(1.minutes + 30.seconds, h.state.streak)
    }
}
