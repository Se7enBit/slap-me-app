package com.noikonomist.slapme.engine

import com.noikonomist.slapme.engine.EngineEffect.EndLockout
import com.noikonomist.slapme.engine.EngineEffect.Slap
import com.noikonomist.slapme.engine.EngineEffect.StartLockout
import com.noikonomist.slapme.engine.EngineEffect.Warn
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockoutTest {

    private val h = EngineHarness()

    @Test
    fun slapAtTheLastLevelStartsALockout() {
        val effects = h.reachLockout()
        val lockoutStart = h.now

        assertEquals(
            listOf(StartLockout(lockoutStart.toEpochMilli() + 15.minutes.inWholeMilliseconds)),
            effects.ofType<StartLockout>(),
        )
        assertTrue(h.state.inLockout)
    }

    @Test
    fun countingDuringLockoutSlapsAfterTheGrace() {
        h.reachLockout()
        assertEquals(
            listOf(Slap(SlapReason.LOCKOUT, level = 2, packageName = FEED)),
            h.use(5.seconds).ofType<Slap>(),
        )
    }

    @Test
    fun usageShorterThanTheGraceIsNotSlapped() {
        h.reachLockout()
        h.observe(HOME)
        h.idle(1.seconds)

        assertTrue(h.use(4.seconds).ofType<Slap>().isEmpty())
        h.idle(1.seconds)
        assertTrue("any idle restarts the grace", h.use(4.seconds).ofType<Slap>().isEmpty())
    }

    @Test
    fun eachGraceOfCountingIsAnotherSlap() {
        h.reachLockout()
        assertEquals(12, h.use(1.minutes).ofType<Slap>().size)
    }

    @Test
    fun lockoutSlapsDoNotExtendTheLockout() {
        h.reachLockout()
        val until = h.state.lockoutUntilMillis
        h.use(3.minutes)
        assertEquals(until, h.state.lockoutUntilMillis)
        assertEquals(2, h.state.level)
    }

    @Test
    fun idlingDoesNotShortenTheLockout() {
        h.reachLockout()
        h.idle(14.minutes)
        assertTrue(h.state.inLockout)
        assertEquals("level is not reset by idle during lockout", 2, h.state.level)
        assertEquals(SlapReason.LOCKOUT, h.use(5.seconds).ofType<Slap>().single().reason)
    }

    @Test
    fun lockoutEndsWhenItsTimerExpiresAndEscalationRestarts() {
        h.reachLockout()
        val effects = h.idle(15.minutes)

        assertEquals(listOf(EndLockout), effects.ofType<EndLockout>())
        assertNull(h.state.lockoutUntilMillis)
        assertEquals(0, h.state.level)
        assertTrue(h.use(9.minutes + 55.seconds).ofType<Slap>().isEmpty())
        assertEquals(SlapReason.THRESHOLD, h.use(5.seconds).ofType<Slap>().single().reason)
    }

    @Test
    fun lockoutEndsWhileUsingAndStreakStartsFromZero() {
        h.reachLockout()
        h.use(15.minutes)
        assertNull(h.state.lockoutUntilMillis)
        assertTrue(h.use(9.minutes + 55.seconds).ofType<Slap>().isEmpty())
    }

    @Test
    fun noWarningsDuringLockout() {
        h.reachLockout()
        assertTrue(h.use(5.minutes).ofType<Warn>().isEmpty())
    }
}
