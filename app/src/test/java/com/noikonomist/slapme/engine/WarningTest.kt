package com.noikonomist.slapme.engine

import com.noikonomist.slapme.engine.EngineEffect.CancelWarning
import com.noikonomist.slapme.engine.EngineEffect.Warn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarningTest {

    private fun at(offset: Duration) = MONDAY_10AM.plusMillis(offset.inWholeMilliseconds)

    @Test
    fun warnsOnceAtTheConfiguredLeadBeforeTheSlap() {
        for (lead in listOf(30.seconds, 1.minutes, 2.minutes)) {
            val h = EngineHarness(config = EngineConfig(warningLead = lead))
            val warnings = h.use(10.minutes).ofType<Warn>()

            assertEquals("lead $lead", 1, warnings.size)
            assertEquals(at(10.minutes).toEpochMilli(), warnings.single().slapAtMillis)
            assertEquals(at(10.minutes - lead), h.timeOf { it is Warn })
        }
    }

    @Test
    fun noWarningWhenOff() {
        val h = EngineHarness(config = EngineConfig(warningLead = null))
        assertTrue(h.use(10.minutes).ofType<Warn>().isEmpty())
    }

    @Test
    fun breakCancelsTheWarningAndTheNextStreakWarnsAgain() {
        val h = EngineHarness()
        assertEquals(1, h.use(9.minutes + 30.seconds).ofType<Warn>().size)

        assertEquals(listOf(CancelWarning), h.idle(60.seconds).ofType<CancelWarning>())
        assertTrue(h.use(8.minutes + 55.seconds).ofType<Warn>().isEmpty())
        assertEquals(1, h.use(5.seconds).ofType<Warn>().size)
    }

    @Test
    fun shortIdleKeepsTheWarning() {
        val h = EngineHarness()
        h.use(9.minutes + 10.seconds)
        val effects = h.idle(30.seconds) + h.use(50.seconds)
        assertTrue(effects.ofType<CancelWarning>().isEmpty())
        assertTrue("no second warning", effects.ofType<Warn>().isEmpty())
    }

    @Test
    fun thresholdShorterThanTheLeadWarnsAsSoonAsCountingStarts() {
        val h = EngineHarness(config = EngineConfig(levels = listOf(1.minutes), warningLead = 2.minutes))
        val warning = h.use(5.seconds).ofType<Warn>().single()

        assertEquals(MONDAY_10AM, h.timeOf { it is Warn })
        assertEquals(at(1.minutes).toEpochMilli(), warning.slapAtMillis)
    }

    @Test
    fun warningSaysWhetherTheFreeSnoozeIsLeft() {
        val h = EngineHarness()
        assertTrue(h.use(9.minutes).ofType<Warn>().single().freeSnoozeAvailable)

        h.snooze()
        h.use(10.minutes) // slapped at the end of the snooze
        h.idle(60.seconds)
        val next = h.use(5.minutes).ofType<Warn>().single()
        assertEquals(false, next.freeSnoozeAvailable)
    }

    @Test
    fun noWarningWhileIdle() {
        val h = EngineHarness()
        h.use(8.minutes + 30.seconds)
        assertTrue(h.idle(50.seconds).ofType<Warn>().isEmpty())
    }
}
