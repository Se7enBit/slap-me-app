package com.noikonomist.slapme.ui.override

import com.noikonomist.slapme.data.settings.Settings
import com.noikonomist.slapme.engine.ActivationRules
import com.noikonomist.slapme.engine.DaysMask
import com.noikonomist.slapme.engine.FocusSession
import com.noikonomist.slapme.engine.MONDAY_10AM
import com.noikonomist.slapme.engine.ScheduleBlock
import com.noikonomist.slapme.engine.TEST_ZONE
import com.noikonomist.slapme.monitor.MonitorStatus
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverrideLogicTest {

    private val now = MONDAY_10AM
    private val nowMs = now.toEpochMilli()

    // --- FrictionGate -------------------------------------------------------------------------

    private val gate = FrictionGate(startedAtMillis = nowMs, waitMillis = 15_000, phrase = "I choose to be distracted")

    @Test
    fun countdownRunsFor15Seconds() {
        assertEquals(15, gate.secondsLeft(nowMs))
        assertEquals(15, gate.secondsLeft(nowMs + 200))
        assertEquals(1, gate.secondsLeft(nowMs + 14_001))
        assertEquals(0, gate.secondsLeft(nowMs + 15_000))
        assertFalse(gate.unlocked(nowMs + 14_999))
        assertTrue(gate.unlocked(nowMs + 15_000))
    }

    @Test
    fun submitNeedsTheWaitAndTheExactPhrase() {
        assertFalse("too early", gate.canSubmit(nowMs + 10_000, "I choose to be distracted"))
        assertFalse("wrong phrase", gate.canSubmit(nowMs + 16_000, "let me in"))
        assertTrue(gate.canSubmit(nowMs + 16_000, "i choose to be distracted."))
    }

    // --- OverrideOptions ----------------------------------------------------------------------

    private val settings = Settings()
    private val watching = MonitorStatus(running = true, armed = true, active = true, freeSnoozeAvailable = true)

    @Test
    fun freeSnoozeFirstThenFriction() {
        assertEquals(OverrideChoice.FreeSnooze(5.minutes), OverrideOptions.choose(watching, settings, OverridePurpose.MORE_TIME))
        assertEquals(
            OverrideChoice.Friction(5.minutes),
            OverrideOptions.choose(watching.copy(freeSnoozeAvailable = false), settings, OverridePurpose.MORE_TIME),
        )
    }

    @Test
    fun lockoutStillOffersTheOverride() {
        val locked = watching.copy(freeSnoozeAvailable = false, lockoutUntilMillis = nowMs + 60_000)
        assertEquals(OverrideChoice.Friction(5.minutes), OverrideOptions.choose(locked, settings, OverridePurpose.MORE_TIME))
    }

    @Test
    fun nothingToOverrideWhenNotWatching() {
        assertEquals(OverrideChoice.NothingToOverride, OverrideOptions.choose(MonitorStatus(), settings, OverridePurpose.MORE_TIME))
        assertEquals(OverrideChoice.NothingToOverride, OverrideOptions.choose(watching.copy(active = false), settings, OverridePurpose.MORE_TIME))
    }

    @Test
    fun switchingOffAlwaysNeedsFriction() {
        assertEquals(OverrideChoice.SwitchOff, OverrideOptions.choose(watching, settings, OverridePurpose.SWITCH_OFF))
    }

    // --- SwitchOffGate --------------------------------------------------------------------------

    private val lockedUntil = nowMs + 10.minutes.inWholeMilliseconds
    private val session = FocusSession(1, nowMs - 60_000, null)
    private val currentBlock = ScheduleBlock(5, DaysMask.ALL, 9 * 60, 12 * 60)
    private val laterBlock = ScheduleBlock(6, DaysMask.ALL, 14 * 60, 15 * 60)

    private fun gated(action: SwitchOff, rules: ActivationRules, lockout: Long? = lockedUntil) =
        SwitchOffGate.needsFriction(action, rules, lockout, now, TEST_ZONE)

    @Test
    fun outsideALockoutNothingIsGated() {
        assertFalse(gated(SwitchOff.StopSession, ActivationRules(session = session), lockout = null))
        assertFalse("lockout already over", gated(SwitchOff.StopSession, ActivationRules(session = session), lockout = nowMs - 1))
    }

    @Test
    fun stoppingTheOnlyThingWatchingIsGated() {
        assertTrue(gated(SwitchOff.StopSession, ActivationRules(session = session)))
        assertTrue(gated(SwitchOff.AlwaysOnOff, ActivationRules(alwaysOn = true)))
        assertTrue(gated(SwitchOff.UpdateBlock(currentBlock.copy(enabled = false)), ActivationRules(blocks = listOf(currentBlock))))
        assertTrue(gated(SwitchOff.DeleteBlock(currentBlock.id), ActivationRules(blocks = listOf(currentBlock))))
    }

    @Test
    fun movingTheCurrentBlockAwayFromNowIsGated() {
        val moved = currentBlock.copy(startMinute = 11 * 60)
        assertTrue(gated(SwitchOff.UpdateBlock(moved), ActivationRules(blocks = listOf(currentBlock))))
    }

    @Test
    fun changesThatKeepItWatchingAreFree() {
        assertFalse("always-on still watches", gated(SwitchOff.StopSession, ActivationRules(session = session, alwaysOn = true)))
        assertFalse("block still watches", gated(SwitchOff.AlwaysOnOff, ActivationRules(blocks = listOf(currentBlock), alwaysOn = true)))
        assertFalse(
            "a different block",
            gated(SwitchOff.UpdateBlock(laterBlock.copy(enabled = false)), ActivationRules(blocks = listOf(currentBlock, laterBlock))),
        )
        assertFalse("not active anyway", gated(SwitchOff.DeleteBlock(laterBlock.id), ActivationRules(blocks = listOf(laterBlock))))
    }

    @Test
    fun rulesAfterEachSwitchOff() {
        val rules = ActivationRules(blocks = listOf(currentBlock, laterBlock), session = session, alwaysOn = true)
        assertEquals(rules.copy(session = null), SwitchOff.StopSession.rulesAfter(rules))
        assertEquals(rules.copy(alwaysOn = false), SwitchOff.AlwaysOnOff.rulesAfter(rules))
        assertEquals(listOf(laterBlock), SwitchOff.DeleteBlock(currentBlock.id).rulesAfter(rules).blocks)
        assertEquals(
            listOf(currentBlock.copy(enabled = false), laterBlock),
            SwitchOff.UpdateBlock(currentBlock.copy(enabled = false)).rulesAfter(rules).blocks,
        )
    }
}
