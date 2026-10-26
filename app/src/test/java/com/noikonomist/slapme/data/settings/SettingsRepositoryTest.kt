package com.noikonomist.slapme.data.settings

import androidx.datastore.preferences.core.edit
import com.noikonomist.slapme.data.testPreferencesStore
import com.noikonomist.slapme.engine.CountingMode
import com.noikonomist.slapme.engine.EngineConfig
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.repository() = SettingsRepository(testPreferencesStore(backgroundScope, tmp, "settings"))

    @Test
    fun emptyStoreGivesTheDefaults() = runTest {
        val settings = repository().current()

        assertEquals(Settings(), settings)
        assertEquals(EngineConfig.Defaults.levels, settings.engine.levels)
        assertEquals(EngineConfig.Defaults.navigationPackages, settings.navigationPackages)
        assertNull("daily cap is off by default", settings.engine.dailyCap)
        assertEquals(CountingMode.SCREEN_ON, settings.engine.countingMode)
    }

    @Test
    fun everyFieldRoundTrips() = runTest {
        val custom = Settings(
            engine = EngineConfig(
                levels = listOf(20.seconds, 10.seconds, 5.seconds),
                lockoutDuration = 30.minutes,
                lockoutGrace = 0.seconds,
                streakResetBreak = 90.seconds,
                escalationResetBreak = 20.minutes,
                dailyCap = 3.hours,
                warningLead = 30.seconds,
                snoozeDuration = 3.minutes,
                frictionOverrideDuration = 7.minutes,
                countingMode = CountingMode.WHITELIST,
                blacklist = setOf("com.a", "com.b"),
                whitelist = setOf("com.c"),
            ),
            alwaysOn = true,
            alwaysPlaySound = true,
            navigationPackages = setOf("com.waze"),
        )
        val repo = repository()
        repo.update { custom }

        assertEquals(custom, repo.current())
    }

    @Test
    fun turningTheWarningAndTheCapOffIsStored() = runTest {
        val repo = repository()
        repo.update { it.copy(engine = it.engine.copy(warningLead = null, dailyCap = 2.hours)) }
        repo.update { it.copy(engine = it.engine.copy(dailyCap = null)) }

        val engine = repo.current().engine
        assertNull(engine.warningLead)
        assertNull(engine.dailyCap)
    }

    @Test
    fun levelsKeepTheirOrder() = runTest {
        val repo = repository()
        val levels = listOf(2.minutes, 10.minutes, 5.minutes)
        repo.update { it.copy(engine = it.engine.copy(levels = levels)) }
        assertEquals(levels, repo.current().engine.levels)
    }

    @Test
    fun clearedNavigationListStaysEmpty() = runTest {
        val repo = repository()
        repo.update { it.copy(navigationPackages = emptySet()) }
        assertEquals(emptySet<String>(), repo.current().navigationPackages)
    }

    @Test
    fun invalidStoredValuesFallBackFieldByField() = runTest {
        val store = testPreferencesStore(backgroundScope, tmp, "settings")
        store.edit {
            it[SettingsCodec.Keys.LEVELS_MS] = "abc,-5"
            it[SettingsCodec.Keys.LOCKOUT_MS] = -1
            it[SettingsCodec.Keys.COUNTING_MODE] = "NONSENSE"
            it[SettingsCodec.Keys.STREAK_RESET_BREAK_MS] = 120_000
            it[SettingsCodec.Keys.DAILY_CAP_MS] = 0
        }
        val engine = SettingsRepository(store).current().engine

        assertEquals(EngineConfig.Defaults.levels, engine.levels)
        assertEquals(EngineConfig.Defaults.lockoutDuration, engine.lockoutDuration)
        assertEquals(EngineConfig.Defaults.countingMode, engine.countingMode)
        assertEquals("valid values are kept", 120.seconds, engine.streakResetBreak)
        assertNull(engine.dailyCap)
    }

    @Test
    fun partlyInvalidLevelsKeepTheValidOnes() = runTest {
        val store = testPreferencesStore(backgroundScope, tmp, "settings")
        store.edit { it[SettingsCodec.Keys.LEVELS_MS] = "600000,x,0,120000" }
        assertEquals(listOf(10.minutes, 2.minutes), SettingsRepository(store).current().engine.levels)
    }

    @Test
    fun concurrentUpdatesAreBothApplied() = runTest {
        val repo = repository()
        val a = launch { repo.update { it.copy(alwaysOn = true) } }
        val b = launch { repo.update { it.copy(alwaysPlaySound = true) } }
        a.join()
        b.join()

        val settings = repo.current()
        assertTrue(settings.alwaysOn)
        assertTrue(settings.alwaysPlaySound)
    }

    @Test
    fun flowEmitsTheLatestSettings() = runTest {
        val repo = repository()
        repo.update { it.copy(alwaysOn = true) }
        assertTrue(repo.settings.first().alwaysOn)
    }
}
