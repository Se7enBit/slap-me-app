package com.noikonomist.slapme.ui.settings

import com.noikonomist.slapme.data.settings.Settings
import com.noikonomist.slapme.engine.EngineConfig
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsEditingTest {

    @Test
    fun addingALevelHalvesTheLastOneButNotBelowOneMinute() {
        assertEquals(listOf(10.minutes, 5.minutes, 2.minutes, 1.minutes), LevelsEditor.add(EngineConfig.Defaults.levels))
        assertEquals(listOf(10.minutes, 5.minutes), LevelsEditor.add(listOf(10.minutes)))
        assertEquals(listOf(1.minutes, 1.minutes), LevelsEditor.add(listOf(1.minutes)))
    }

    @Test
    fun atMostFiveLevelsAndAtLeastOne() {
        val five = List(5) { 10.minutes }
        assertEquals(five, LevelsEditor.add(five))
        assertEquals(listOf(10.minutes), LevelsEditor.remove(listOf(10.minutes), 0))
        assertEquals(listOf(10.minutes, 2.minutes), LevelsEditor.remove(EngineConfig.Defaults.levels, 1))
    }

    @Test
    fun steppingSnapsToTheGridAndClamps() {
        val range = 1.minutes..120.minutes
        assertEquals(11.minutes, Stepper.step(10.minutes, +1, 1.minutes, range))
        assertEquals(9.minutes, Stepper.step(10.minutes, -1, 1.minutes, range))
        assertEquals(1.minutes, Stepper.step(1.minutes, -1, 1.minutes, range))
        assertEquals(120.minutes, Stepper.step(120.minutes, +1, 1.minutes, range))
        // Debug values in seconds snap onto whole steps.
        assertEquals(1.minutes, Stepper.step(30.seconds, +1, 1.minutes, range))
        assertEquals(1.minutes, Stepper.step(90.seconds, -1, 1.minutes, range))
        assertEquals(2.minutes, Stepper.step(90.seconds, +1, 1.minutes, range))
    }

    @Test
    fun appListsReadAndWriteTheRightSetting() {
        val base = Settings()
        val picked = setOf("com.a")
        assertEquals(picked, AppListKind.DISTRACTING.selected(AppListKind.DISTRACTING.withSelected(base, picked)))
        assertEquals(picked, AppListKind.DISTRACTING.withSelected(base, picked).engine.blacklist)
        assertEquals(picked, AppListKind.ALLOWED.withSelected(base, picked).engine.whitelist)
        assertEquals(picked, AppListKind.NAVIGATION.withSelected(base, picked).navigationPackages)
        assertEquals(EngineConfig.Defaults.navigationPackages, AppListKind.NAVIGATION.selected(base))
    }

    @Test
    fun turningTheDailyCapOnStartsAtTwoHours() {
        assertEquals(2.hours, SettingOptions.dailyCapWhenTurnedOn)
    }
}
