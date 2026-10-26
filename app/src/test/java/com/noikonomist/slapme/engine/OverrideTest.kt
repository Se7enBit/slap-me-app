package com.noikonomist.slapme.engine

import com.noikonomist.slapme.engine.EngineEffect.CancelWarning
import com.noikonomist.slapme.engine.EngineEffect.LogOverride
import com.noikonomist.slapme.engine.EngineEffect.OverrideRejected
import com.noikonomist.slapme.engine.EngineEffect.Slap
import com.noikonomist.slapme.engine.EngineEffect.Warn
import java.time.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverrideTest {

    private val h = EngineHarness()

    private fun minutesIn(n: Int): Instant = MONDAY_10AM.plusSeconds(n * 60L)

    @Test
    fun freeSnoozeHoldsTheSlapBackForFiveMinutes() {
        h.use(9.minutes)
        val effects = h.snooze()

        assertEquals(listOf(LogOverride(OverrideType.SNOOZE, 5.minutes)), effects.ofType<LogOverride>())
        assertEquals("the pending warning is cancelled", listOf(CancelWarning), effects.ofType<CancelWarning>())

        assertTrue(h.use(4.minutes + 55.seconds).ofType<Slap>().isEmpty())
        assertEquals(1, h.use(5.seconds).ofType<Slap>().size)
        assertEquals(minutesIn(14), h.timeOf { it is Slap })
    }

    @Test
    fun warnsAgainBeforeTheSnoozeEnds() {
        h.use(9.minutes)
        h.snooze()
        val warning = h.use(5.minutes).ofType<Warn>().single()

        assertEquals(minutesIn(14).toEpochMilli(), warning.slapAtMillis)
        assertFalse(warning.freeSnoozeAvailable)
        assertEquals(minutesIn(13), h.log.last { it.effect is Warn }.at)
    }

    @Test
    fun secondSnoozeInTheSamePeriodIsRejected() {
        h.use(9.minutes)
        h.snooze()
        val second = h.snooze()

        assertEquals(listOf(OverrideRejected(OverrideRejection.FREE_SNOOZE_USED)), second)
        assertFalse(h.engine.freeSnoozeAvailable(h.state))
    }

    @Test
    fun frictionOverrideIsAlwaysGranted() {
        h.snooze()
        assertEquals(listOf(LogOverride(OverrideType.FRICTION, 5.minutes)), h.friction().ofType<LogOverride>())
        assertEquals(listOf(LogOverride(OverrideType.FRICTION, 5.minutes)), h.friction().ofType<LogOverride>())
    }

    @Test
    fun overlappingOverridesDoNotShortenEachOther() {
        h.friction()
        h.use(4.minutes)
        h.snooze()
        assertEquals(minutesIn(9).toEpochMilli(), h.state.overrideUntilMillis)

        h.friction() // granted at 4 min, would end at 9: keeps the later end
        assertEquals(minutesIn(9).toEpochMilli(), h.state.overrideUntilMillis)
    }

    @Test
    fun alwaysOnGetsOneFreeSnoozePerCalendarDay() {
        h.snooze()
        assertFalse(h.engine.freeSnoozeAvailable(h.state))

        h.idle(1.days)
        assertTrue(h.engine.freeSnoozeAvailable(h.state))
    }

    @Test
    fun eachScheduleBlockOccurrenceGetsItsOwnFreeSnooze() {
        h.rules = ActivationRules(
            blocks = listOf(
                ScheduleBlock(1, DaysMask.ALL, startMinute = 9 * 60, endMinute = 12 * 60),
                ScheduleBlock(2, DaysMask.ALL, startMinute = 13 * 60, endMinute = 17 * 60),
            ),
        )
        h.snooze()
        assertFalse(h.engine.freeSnoozeAvailable(h.state))

        h.idle(4.hours) // 14:00, second block
        assertTrue(h.engine.freeSnoozeAvailable(h.state))
        h.snooze()

        h.idle(20.hours) // next day 10:00, first block again
        assertTrue(h.engine.freeSnoozeAvailable(h.state))
    }

    @Test
    fun focusSessionGetsItsOwnFreeSnooze() {
        h.snooze()
        h.rules = ActivationRules(alwaysOn = true, session = FocusSession(5, MONDAY_10AM.toEpochMilli(), null))
        assertTrue(h.engine.freeSnoozeAvailable(h.state))
    }

    @Test
    fun overridesWorkDuringLockoutWithoutStoppingItsTimer() {
        h.reachLockout()
        val until = h.state.lockoutUntilMillis
        h.friction()

        assertTrue(h.use(4.minutes + 55.seconds).ofType<Slap>().isEmpty())
        assertEquals(SlapReason.LOCKOUT, h.use(5.seconds).ofType<Slap>().single().reason)
        assertEquals(until, h.state.lockoutUntilMillis)
    }

    @Test
    fun freeSnoozeIsUsableDuringLockout() {
        h.reachLockout()
        assertEquals(1, h.snooze().ofType<LogOverride>().size)
        assertTrue(h.use(4.minutes).ofType<Slap>().isEmpty())
    }

    @Test
    fun breaksStillResetTheStreakDuringAnOverride() {
        h.use(9.minutes)
        h.snooze()
        h.idle(60.seconds)
        assertEquals(0.seconds, h.state.streak)
        assertTrue(h.use(9.minutes).ofType<Slap>().isEmpty())
    }

    @Test
    fun nothingToOverrideWhenNotActive() {
        h.rules = ActivationRules()
        h.use(1.minutes)

        assertEquals(listOf(OverrideRejected(OverrideRejection.NOT_ACTIVE)), h.snooze())
        assertEquals(listOf(OverrideRejected(OverrideRejection.NOT_ACTIVE)), h.friction())
        assertFalse(h.engine.freeSnoozeAvailable(h.state))
    }

    @Test
    fun frictionPhraseIsForgivingAboutCaseSpacesAndAFinalPeriod() {
        assertTrue(FrictionPhrase.matches("I choose to be distracted"))
        assertTrue(FrictionPhrase.matches("i choose to be DISTRACTED"))
        assertTrue(FrictionPhrase.matches("  I  choose to   be distracted.  "))
        assertFalse(FrictionPhrase.matches("I choose to be distracte"))
        assertFalse(FrictionPhrase.matches("I choose not to be distracted"))
        assertFalse(FrictionPhrase.matches(""))
    }

    @Test
    fun frictionPhraseCanBeSuppliedByTheUi() {
        assertTrue(FrictionPhrase.matches("elijo distraerme", phrase = "Elijo distraerme"))
    }
}
