package com.noikonomist.slapme.ui.home

import com.noikonomist.slapme.data.settings.Settings
import com.noikonomist.slapme.engine.ActivationRules
import com.noikonomist.slapme.engine.DaysMask
import com.noikonomist.slapme.engine.MONDAY_10AM
import com.noikonomist.slapme.engine.ScheduleBlock
import com.noikonomist.slapme.engine.TEST_ZONE
import com.noikonomist.slapme.engine.UsageState
import com.noikonomist.slapme.engine.instantAt
import com.noikonomist.slapme.monitor.MonitorStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeStateTest {

    private val now = MONDAY_10AM.toEpochMilli()
    private val settings = Settings()
    private val alwaysOn = ActivationRules(alwaysOn = true)
    private val running = MonitorStatus(
        running = true, armed = true, active = true, usage = UsageState.PAUSED,
        streakMillis = 250_000, thresholdMillis = 600_000, level = 1,
    )

    private fun state(status: MonitorStatus, rules: ActivationRules, at: Long = now) =
        HomeState.from(status, rules, settings, at, TEST_ZONE)

    @Test
    fun offWhenNothingIsEnabled() {
        assertEquals(HomeState.Off, state(MonitorStatus(), ActivationRules()))
    }

    @Test
    fun armedShowsTheNextFocusTime() {
        val rules = ActivationRules(blocks = listOf(ScheduleBlock(1, DaysMask.ALL, 14 * 60, 15 * 60)))
        assertEquals(HomeState.Armed(instantAt("2026-10-26", "14:00:00").toEpochMilli()), state(running.copy(active = false), rules))
    }

    @Test
    fun activeFromTheMonitorStatus() {
        assertEquals(
            HomeState.Active(streakMillis = 250_000, thresholdMillis = 600_000, level = 1, levelCount = 3, usage = UsageState.PAUSED, snoozedUntilMillis = null),
            state(running, alwaysOn),
        )
    }

    @Test
    fun activeBeforeTheMonitorReportsUsesTheBaseThreshold() {
        assertEquals(
            HomeState.Active(streakMillis = 0, thresholdMillis = 600_000, level = 0, levelCount = 3, usage = UsageState.IDLE, snoozedUntilMillis = null),
            state(MonitorStatus(), alwaysOn),
        )
    }

    @Test
    fun lockoutCountsDown() {
        val status = running.copy(lockoutUntilMillis = now + 90_000)
        assertEquals(HomeState.Lockout(untilMillis = now + 90_000, remainingMillis = 90_000), state(status, alwaysOn))
    }

    @Test
    fun expiredLockoutIsNotShown() {
        val status = running.copy(lockoutUntilMillis = now - 1)
        assertEquals(HomeState.Active::class, state(status, alwaysOn)::class)
    }

    @Test
    fun snoozeIsShownWhileItLasts() {
        val status = running.copy(overrideUntilMillis = now + 60_000)
        assertEquals(now + 60_000, (state(status, alwaysOn) as HomeState.Active).snoozedUntilMillis)
        assertEquals(null, (state(status, alwaysOn, at = now + 61_000) as HomeState.Active).snoozedUntilMillis)
    }

    @Test
    fun lockoutOutsideFocusTimeShowsArmed() {
        val rules = ActivationRules(blocks = listOf(ScheduleBlock(1, DaysMask.ALL, 14 * 60, 15 * 60)))
        val status = running.copy(active = false, lockoutUntilMillis = now + 90_000)
        assertEquals(HomeState.Armed::class, state(status, rules)::class)
    }
}
