package com.noikonomist.slapme.data.settings

import com.noikonomist.slapme.data.testPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OnboardingRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun startsNotDone() = runTest {
        val repo = OnboardingRepository(testPreferencesStore(backgroundScope, tmp, "settings"))
        assertEquals(OnboardingState(), repo.state.first())
    }

    @Test
    fun flagsAreStored() = runTest {
        val repo = OnboardingRepository(testPreferencesStore(backgroundScope, tmp, "settings"))
        repo.acceptUsageDisclosure()
        repo.dismissBatteryTip(atMillis = 1_234)
        repo.complete()

        assertEquals(
            OnboardingState(completed = true, usageDisclosureAccepted = true, batteryTipDismissedAtMillis = 1_234),
            repo.state.first(),
        )
    }

    @Test
    fun sharesTheSettingsFileWithoutClobberingSettings() = runTest {
        val store = testPreferencesStore(backgroundScope, tmp, "settings")
        val settings = SettingsRepository(store)
        val onboarding = OnboardingRepository(store)

        settings.update { it.copy(alwaysOn = true) }
        onboarding.complete()
        settings.update { it.copy(alwaysPlaySound = true) }

        assertTrue(onboarding.state.first().completed)
        assertTrue(settings.current().alwaysOn)
        assertTrue(settings.current().alwaysPlaySound)
    }
}
