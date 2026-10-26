package com.noikonomist.slapme.data.settings

import androidx.datastore.preferences.core.edit
import com.noikonomist.slapme.data.testPreferencesStore
import com.noikonomist.slapme.engine.DailyStats
import com.noikonomist.slapme.engine.EngineEffect.EndLockout
import com.noikonomist.slapme.engine.EngineEffect.Slap
import com.noikonomist.slapme.engine.EngineHarness
import com.noikonomist.slapme.engine.EngineState
import com.noikonomist.slapme.engine.SlapReason
import com.noikonomist.slapme.engine.UsageState
import com.noikonomist.slapme.engine.ofType
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EngineStateRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.store() = testPreferencesStore(backgroundScope, tmp, "engine_state")

    private val busyState = EngineState(
        lastEventAtMillis = 1_761_472_800_000,
        active = true,
        usage = UsageState.COUNTING,
        foregroundPackage = "com.example.feed",
        streakMillis = 123_000,
        idleRunMillis = 4_000,
        level = 2,
        lockoutUntilMillis = 1_761_473_700_000,
        lockoutCountingMillis = 3_000,
        overrideUntilMillis = 1_761_473_000_000,
        warningIssued = true,
        usedSnoozePeriods = setOf("day:2026-10-26", "session:4"),
        today = DailyStats(LocalDate.parse("2026-10-26"), countedMillis = 3_600_000, longestFocusMillis = 1_200_000),
        focusRunMillis = 600_000,
        reportedExpiredSessionId = 4,
    )

    @Test
    fun emptyStoreGivesAFreshState() = runTest {
        assertEquals(EngineState(), EngineStateRepository(store()).load())
    }

    @Test
    fun everyFieldRoundTrips() = runTest {
        val repo = EngineStateRepository(store())
        repo.save(busyState)
        assertEquals(busyState, repo.load())
    }

    @Test
    fun initialStateRoundTrips() = runTest {
        val repo = EngineStateRepository(store())
        repo.save(EngineState())
        assertEquals(EngineState(), repo.load())
    }

    @Test
    fun savingReplacesFieldsThatBecameNull() = runTest {
        val repo = EngineStateRepository(store())
        repo.save(busyState)
        repo.save(busyState.copy(lockoutUntilMillis = null, overrideUntilMillis = null, foregroundPackage = null, today = null))

        val loaded = repo.load()
        assertNull(loaded.lockoutUntilMillis)
        assertNull(loaded.overrideUntilMillis)
        assertNull(loaded.foregroundPackage)
        assertNull(loaded.today)
    }

    @Test
    fun unknownVersionStartsFresh() = runTest {
        val store = store()
        val repo = EngineStateRepository(store)
        repo.save(busyState)
        store.edit { it[EngineStateCodec.Keys.VERSION] = 99 }

        assertEquals(EngineState(), repo.load())
    }

    @Test
    fun unreadableValueStartsFresh() = runTest {
        val store = store()
        val repo = EngineStateRepository(store)
        repo.save(busyState)
        store.edit { it[EngineStateCodec.Keys.USAGE] = "SCROLLING" }

        assertEquals(EngineState(), repo.load())
    }

    @Test
    fun lockoutSurvivesARestartThroughTheStore() = runTest {
        val before = EngineHarness()
        before.reachLockout()
        val until = before.state.lockoutUntilMillis

        val repo = EngineStateRepository(store())
        repo.save(before.state)
        val after = EngineHarness(start = before.now.plusSeconds(180), state = repo.load())

        assertEquals(SlapReason.LOCKOUT, after.use(5.seconds).ofType<Slap>().single().reason)
        assertEquals(until, after.state.lockoutUntilMillis)
        after.idle(12.minutes)
        assertEquals(listOf(EndLockout), after.log.map { it.effect }.ofType<EndLockout>())
        assertEquals(0, after.state.level)
    }
}
