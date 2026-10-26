package com.noikonomist.slapme.monitor

import com.noikonomist.slapme.data.settings.Settings
import com.noikonomist.slapme.engine.ActivationRules
import com.noikonomist.slapme.engine.DailyStats
import com.noikonomist.slapme.engine.EngineConfig
import com.noikonomist.slapme.engine.EngineEffect
import com.noikonomist.slapme.engine.EngineState
import com.noikonomist.slapme.engine.MONDAY_10AM
import com.noikonomist.slapme.engine.MutableClock
import com.noikonomist.slapme.engine.OverrideRejection
import com.noikonomist.slapme.engine.TEST_ZONE
import com.noikonomist.slapme.engine.using
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorOverrideTest {

    private object NoStore : MonitorStore {
        override suspend fun saveState(state: EngineState) = Unit
        override suspend fun record(effects: List<EngineEffect>, atMillis: Long) = Unit
        override suspend fun recordToday(stats: DailyStats) = Unit
        override suspend fun serviceGap(atMillis: Long, duration: Duration) = Unit
    }

    private object NoActions : MonitorActions {
        override fun warn(slapAtMillis: Long, freeSnoozeAvailable: Boolean) = Unit
        override fun cancelWarning() = Unit
        override suspend fun slap(alwaysPlaySound: Boolean) = Unit
        override fun overrideRejected(reason: OverrideRejection) = Unit
    }

    private val fast = Settings(
        engine = EngineConfig(
            levels = listOf(30.seconds, 15.seconds, 10.seconds),
            lockoutDuration = 60.seconds,
            warningLead = null,
            snoozeDuration = 20.seconds,
            frictionOverrideDuration = 25.seconds,
        ),
        alwaysOn = true,
    )
    private val clock = MutableClock(MONDAY_10AM, TEST_ZONE)
    private val core = MonitorCore(EngineState(), clock, NoStore, NoActions)
    private val t0 = MONDAY_10AM.toEpochMilli()

    private suspend fun use(duration: Duration) {
        core.observe(using())
        var left = duration
        while (left.isPositive()) {
            clock.advance(5.seconds)
            left -= 5.seconds
            core.observe(using())
        }
    }

    @Test
    fun snoozeReportsWhenItEnds() = runTest {
        core.configure(fast, ActivationRules(alwaysOn = true))
        core.observe(using())
        assertEquals(OverrideResult.Granted(untilMillis = t0 + 20_000), core.snooze())
    }

    @Test
    fun secondSnoozeReportsTheRejection() = runTest {
        core.configure(fast, ActivationRules(alwaysOn = true))
        core.snooze()
        assertEquals(OverrideResult.Rejected(OverrideRejection.FREE_SNOOZE_USED), core.snooze())
    }

    @Test
    fun frictionOverrideIsGrantedEvenInLockoutAndLeavesTheLockoutAlone() = runTest {
        core.configure(fast, ActivationRules(alwaysOn = true))
        use(30.seconds)
        use(15.seconds)
        use(10.seconds)
        val lockoutUntil = core.status.value.lockoutUntilMillis
        assertTrue(lockoutUntil != null)

        val result = core.grantFrictionOverride()
        assertEquals(OverrideResult.Granted(untilMillis = clock.millis() + 25_000), result)
        assertEquals(lockoutUntil, core.status.value.lockoutUntilMillis)
    }

    @Test
    fun nothingToOverrideWhenNotActive() = runTest {
        core.configure(fast.copy(alwaysOn = false), ActivationRules())
        assertEquals(OverrideResult.Rejected(OverrideRejection.NOT_ACTIVE), core.grantFrictionOverride())
    }

    @Test
    fun unconfiguredCoreIsNotRunning() = runTest {
        assertEquals(OverrideResult.NotRunning, core.snooze())
    }

    @Test
    fun statusTellsWhetherTheFreeSnoozeIsLeft() = runTest {
        core.configure(fast, ActivationRules(alwaysOn = true))
        core.observe(using())
        assertTrue(core.status.value.freeSnoozeAvailable)

        core.snooze()
        assertFalse(core.status.value.freeSnoozeAvailable)

        clock.advance(1.days) // a new calendar day is a new always-on period
        core.observe(using())
        assertTrue(core.status.value.freeSnoozeAvailable)
    }

    @Test
    fun bridgeWithoutAMonitorSaysSo() = runTest {
        val bridge = MonitorBridge()
        assertEquals(OverrideResult.NotRunning, bridge.snooze())
        assertEquals(OverrideResult.NotRunning, bridge.grantFrictionOverride())

        core.configure(fast, ActivationRules(alwaysOn = true))
        bridge.attach(core)
        assertEquals(OverrideResult.Granted::class, bridge.snooze()::class)

        bridge.detach(core)
        assertEquals(OverrideResult.NotRunning, bridge.snooze())
    }
}
