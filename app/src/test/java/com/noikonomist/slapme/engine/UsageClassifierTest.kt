package com.noikonomist.slapme.engine

import com.noikonomist.slapme.engine.UsageState.COUNTING
import com.noikonomist.slapme.engine.UsageState.IDLE
import com.noikonomist.slapme.engine.UsageState.PAUSED
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageClassifierTest {

    private val screenOn = EngineConfig(countingMode = CountingMode.SCREEN_ON)
    private val blacklist = EngineConfig(countingMode = CountingMode.BLACKLIST, blacklist = setOf(TIKTOK))
    private val whitelist = EngineConfig(countingMode = CountingMode.WHITELIST, whitelist = setOf(DOCS))
    private val allModes = listOf(screenOn, blacklist, whitelist)

    private fun obs(
        foreground: Foreground,
        interactive: Boolean = true,
        unlocked: Boolean = true,
        inCall: Boolean = false,
    ) = Observation(interactive, unlocked, inCall, foreground)

    private fun classify(o: Observation, config: EngineConfig) = UsageClassifier.classify(o, config)

    @Test
    fun callPausesInEveryModeEvenWithTheScreenOff() {
        for (config in allModes) {
            assertEquals(PAUSED, classify(obs(Foreground.App(TIKTOK), inCall = true), config))
            assertEquals(PAUSED, classify(obs(Foreground.Unknown, interactive = false, inCall = true), config))
        }
    }

    @Test
    fun screenOffOrLockedIsIdleInEveryMode() {
        for (config in allModes) {
            assertEquals(IDLE, classify(obs(Foreground.App(TIKTOK), interactive = false), config))
            assertEquals(IDLE, classify(obs(Foreground.App(TIKTOK), unlocked = false), config))
        }
    }

    @Test
    fun launcherIsIdleInEveryMode() {
        for (config in allModes) {
            assertEquals(IDLE, classify(obs(Foreground.Launcher), config))
        }
    }

    @Test
    fun exemptAppsPauseInEveryMode() {
        for (config in allModes) {
            assertEquals(PAUSED, classify(obs(Foreground.Exempt(MAPS)), config))
        }
    }

    @Test
    fun exemptionWinsOverTheBlacklist() {
        val config = blacklist.copy(blacklist = setOf(MAPS))
        assertEquals(PAUSED, classify(obs(Foreground.Exempt(MAPS)), config))
    }

    @Test
    fun screenOnModeCountsAnyApp() {
        assertEquals(COUNTING, classify(obs(Foreground.App(TIKTOK)), screenOn))
        assertEquals(COUNTING, classify(obs(Foreground.App(DOCS)), screenOn))
        assertEquals(COUNTING, classify(obs(Foreground.Unknown), screenOn))
    }

    @Test
    fun blacklistCountsOnlyListedApps() {
        assertEquals(COUNTING, classify(obs(Foreground.App(TIKTOK)), blacklist))
        assertEquals(IDLE, classify(obs(Foreground.App(DOCS)), blacklist))
        assertEquals(IDLE, classify(obs(Foreground.Unknown), blacklist))
    }

    @Test
    fun whitelistCountsEverythingExceptAllowedApps() {
        assertEquals(COUNTING, classify(obs(Foreground.App(TIKTOK)), whitelist))
        assertEquals(IDLE, classify(obs(Foreground.App(DOCS)), whitelist))
        assertEquals(COUNTING, classify(obs(Foreground.Unknown), whitelist))
    }

    private companion object {
        const val TIKTOK = "com.zhiliaoapp.musically"
        const val DOCS = "com.google.android.apps.docs.editors.docs"
        const val MAPS = "com.google.android.apps.maps"
    }
}
