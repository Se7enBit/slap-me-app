package com.noikonomist.slapme.monitor

import com.noikonomist.slapme.data.settings.Settings
import com.noikonomist.slapme.engine.ActivationRules
import com.noikonomist.slapme.engine.DailyStats
import com.noikonomist.slapme.engine.DaysMask
import com.noikonomist.slapme.engine.EngineConfig
import com.noikonomist.slapme.engine.EngineEffect
import com.noikonomist.slapme.engine.EngineState
import com.noikonomist.slapme.engine.FocusSession
import com.noikonomist.slapme.engine.MONDAY_10AM
import com.noikonomist.slapme.engine.MutableClock
import com.noikonomist.slapme.engine.OverrideRejection
import com.noikonomist.slapme.engine.OverrideType
import com.noikonomist.slapme.engine.SCREEN_OFF
import com.noikonomist.slapme.engine.ScheduleBlock
import com.noikonomist.slapme.engine.TEST_ZONE
import com.noikonomist.slapme.engine.ofType
import com.noikonomist.slapme.engine.using
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorCoreTest {

    private class FakeStore : MonitorStore {
        val saves = mutableListOf<EngineState>()
        val recorded = mutableListOf<EngineEffect>()
        val today = mutableListOf<DailyStats>()
        val gaps = mutableListOf<Duration>()

        override suspend fun saveState(state: EngineState) {
            saves += state
        }

        override suspend fun record(effects: List<EngineEffect>, atMillis: Long) {
            recorded += effects
        }

        override suspend fun recordToday(stats: DailyStats) {
            today += stats
        }

        override suspend fun serviceGap(atMillis: Long, duration: Duration) {
            gaps += duration
        }
    }

    private class FakeActions : MonitorActions {
        val log = mutableListOf<String>()
        override fun warn(slapAtMillis: Long, freeSnoozeAvailable: Boolean) {
            log += "warn"
        }

        override fun cancelWarning() {
            log += "cancel"
        }

        override suspend fun slap(alwaysPlaySound: Boolean) {
            log += "slap(alwaysPlay=$alwaysPlaySound)"
        }

        override fun overrideRejected(reason: OverrideRejection) {
            log += "rejected:$reason"
        }
    }

    /** The debug "fast" preset: a full cycle in a few minutes. */
    private val fast = Settings(
        engine = EngineConfig(
            levels = listOf(30.seconds, 15.seconds, 10.seconds),
            lockoutDuration = 60.seconds,
            lockoutGrace = 5.seconds,
            streakResetBreak = 15.seconds,
            escalationResetBreak = 60.seconds,
            warningLead = 10.seconds,
            snoozeDuration = 20.seconds,
            frictionOverrideDuration = 20.seconds,
        ),
        alwaysOn = true,
    )
    private val noWarning = fast.copy(engine = fast.engine.copy(warningLead = null, levels = listOf(10.minutes)))
    private val alwaysOn = ActivationRules(alwaysOn = true)

    private val clock = MutableClock(MONDAY_10AM, TEST_ZONE)
    private val store = FakeStore()
    private val actions = FakeActions()
    private val core = MonitorCore(EngineState(), clock, store, actions)

    /** Reports app use now and then every 5 s, like the service while the screen is on. */
    private suspend fun use(duration: Duration) {
        core.observe(using())
        var left = duration
        while (left.isPositive()) {
            clock.advance(5.seconds)
            left -= 5.seconds
            core.observe(using())
        }
    }

    private fun slaps() = store.recorded.ofType<EngineEffect.Slap>()

    @Test
    fun ignoresObservationsUntilConfigured() = runTest {
        core.observe(using())
        assertTrue(store.saves.isEmpty())
        assertTrue(actions.log.isEmpty())
        assertFalse(core.status.value.running)
    }

    @Test
    fun fullCycleWarnsSlapsEscalatesAndLocksOut() = runTest {
        core.configure(fast, alwaysOn)

        use(30.seconds)
        assertEquals(listOf("warn", "cancel", "slap(alwaysPlay=false)"), actions.log)

        use(15.seconds)
        use(10.seconds)
        assertEquals(3, slaps().size)
        assertNotNull("third level locks out", core.status.value.lockoutUntilMillis)
        assertEquals(1, store.recorded.ofType<EngineEffect.StartLockout>().size)

        use(5.seconds)
        assertEquals(4, slaps().size)
        assertEquals(com.noikonomist.slapme.engine.SlapReason.LOCKOUT, slaps().last().reason)
    }

    @Test
    fun slapUsesTheAlwaysPlaySetting() = runTest {
        core.configure(fast.copy(alwaysPlaySound = true), alwaysOn)
        use(30.seconds)
        assertEquals("slap(alwaysPlay=true)", actions.log.last())
    }

    @Test
    fun snoozeFromTheNotificationHoldsTheSlapBack() = runTest {
        core.configure(fast, alwaysOn)
        use(20.seconds)
        core.snooze()

        assertEquals(listOf(EngineEffect.LogOverride(OverrideType.SNOOZE, 20.seconds)), store.recorded.ofType<EngineEffect.LogOverride>())
        assertEquals(listOf("warn", "cancel"), actions.log)

        use(15.seconds)
        assertTrue(slaps().isEmpty())
        use(5.seconds)
        assertEquals(1, slaps().size)
    }

    @Test
    fun secondSnoozeIsRejected() = runTest {
        core.configure(fast, alwaysOn)
        use(20.seconds)
        core.snooze()
        core.snooze()
        assertEquals("rejected:FREE_SNOOZE_USED", actions.log.last())
    }

    @Test
    fun frictionOverrideIsLogged() = runTest {
        core.configure(fast, alwaysOn)
        core.grantFrictionOverride()
        assertEquals(listOf(EngineEffect.LogOverride(OverrideType.FRICTION, 20.seconds)), store.recorded.ofType<EngineEffect.LogOverride>())
    }

    @Test
    fun stateIsSavedRightAwayOnImportantChangesAndOtherwiseThrottled() = runTest {
        core.configure(noWarning, alwaysOn)
        core.observe(using()) // first event: becomes active
        val afterStart = store.saves.size
        assertTrue(afterStart >= 1)

        use(25.seconds)
        assertEquals("plain counting ticks are throttled", afterStart, store.saves.size)

        use(5.seconds)
        assertEquals("saved again after 30 s", afterStart + 1, store.saves.size)

        core.snooze()
        assertEquals("override is important", afterStart + 2, store.saves.size)

        core.flush()
        assertEquals(afterStart + 3, store.saves.size)
        assertEquals(core.state, store.saves.last())
    }

    @Test
    fun todaysUsageIsRecordedEveryMinuteAndOnFlush() = runTest {
        core.configure(noWarning, alwaysOn)
        core.observe(using())
        assertEquals(1, store.today.size)

        use(55.seconds)
        assertEquals(1, store.today.size)
        use(5.seconds)
        assertEquals(2, store.today.size)

        core.flush()
        assertEquals(3, store.today.size)
        assertEquals(60.seconds.inWholeMilliseconds, store.today.last().countedMillis)
    }

    @Test
    fun screenOffObservationThenFlushKeepsTheStreakForAShortBreak() = runTest {
        core.configure(noWarning, alwaysOn)
        use(20.seconds)
        core.observe(SCREEN_OFF)
        core.flush()
        assertEquals(20.seconds.inWholeMilliseconds, store.saves.last().streakMillis)
    }

    @Test
    fun reconfiguringRebuildsTheEngineButKeepsTheState() = runTest {
        core.configure(noWarning, alwaysOn)
        use(20.seconds)
        core.configure(noWarning.copy(engine = noWarning.engine.copy(levels = listOf(60.seconds))), alwaysOn)
        assertEquals(20.seconds.inWholeMilliseconds, core.status.value.streakMillis)
        assertEquals(60.seconds.inWholeMilliseconds, core.status.value.thresholdMillis)

        use(35.seconds)
        assertTrue(slaps().isEmpty())
        use(5.seconds)
        assertEquals(1, slaps().size)
    }

    @Test
    fun armedFollowsTheRules() = runTest {
        core.configure(fast, ActivationRules())
        assertFalse(core.isArmed())
        core.configure(fast, alwaysOn)
        assertTrue(core.isArmed())
    }

    @Test
    fun ticksEveryFiveSecondsWhileActive() = runTest {
        core.configure(fast, alwaysOn)
        core.observe(using())
        assertEquals(5.seconds, core.tickDelay())
    }

    @Test
    fun inactiveTicksWaitForTheNextBoundaryButAtMost30Seconds() = runTest {
        val block = ScheduleBlock(1, DaysMask.ALL, startMinute = 10 * 60 + 1, endMinute = 11 * 60)
        core.configure(fast.copy(alwaysOn = false), ActivationRules(blocks = listOf(block)))
        core.observe(using())
        assertEquals("block starts in 60 s", 30.seconds, core.tickDelay())

        clock.advance(50.seconds)
        core.observe(using())
        assertEquals("block starts in 10 s", 10.seconds, core.tickDelay())
    }

    @Test
    fun serviceGapIsStored() = runTest {
        core.configure(noWarning, alwaysOn)
        use(10.seconds)
        clock.advance(2.minutes)
        core.observe(using())
        assertEquals(listOf(2.minutes), store.gaps)
    }

    @Test
    fun expiredSessionIsRecorded() = runTest {
        val session = FocusSession(7, MONDAY_10AM.toEpochMilli(), MONDAY_10AM.toEpochMilli() + 10_000)
        core.configure(noWarning.copy(alwaysOn = false), ActivationRules(session = session))
        use(15.seconds)
        assertEquals(listOf(EngineEffect.SessionExpired(7)), store.recorded.ofType<EngineEffect.SessionExpired>())
        assertFalse(core.isArmed())
    }

    @Test
    fun statusFollowsTheEngine() = runTest {
        core.configure(fast, alwaysOn)
        use(10.seconds)
        val status = core.status.value
        assertTrue(status.running)
        assertTrue(status.armed)
        assertTrue(status.active)
        assertEquals(10.seconds.inWholeMilliseconds, status.streakMillis)
        assertEquals(30.seconds.inWholeMilliseconds, status.thresholdMillis)
        assertEquals("com.example.feed", status.foregroundPackage)
    }
}
