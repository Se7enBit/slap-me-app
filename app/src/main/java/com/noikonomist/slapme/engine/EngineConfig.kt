package com.noikonomist.slapme.engine

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class CountingMode {
    /** Counts while the screen is interactive and unlocked. */
    SCREEN_ON,

    /** Counts only while a listed (distracting) app is in the foreground. */
    BLACKLIST,

    /** Counts everything except listed (allowed) apps. */
    WHITELIST,
}

/**
 * Everything that tunes the engine. Defaults live in [Defaults] and nowhere else.
 *
 * [levels] is the escalation ladder: `levels[0]` is the base threshold, and each slap moves one step
 * further. A slap at the last level starts a lockout.
 */
data class EngineConfig(
    val levels: List<Duration> = Defaults.levels,
    val lockoutDuration: Duration = Defaults.lockoutDuration,
    val lockoutGrace: Duration = Defaults.lockoutGrace,
    val streakResetBreak: Duration = Defaults.streakResetBreak,
    val escalationResetBreak: Duration = Defaults.escalationResetBreak,
    /** Null = daily cap disabled. */
    val dailyCap: Duration? = null,
    /** Null = no warning before the slap. */
    val warningLead: Duration? = Defaults.warningLead,
    val snoozeDuration: Duration = Defaults.snoozeDuration,
    val frictionOverrideDuration: Duration = Defaults.frictionOverrideDuration,
    val countingMode: CountingMode = Defaults.countingMode,
    /** Distracting apps, used in [CountingMode.BLACKLIST]. */
    val blacklist: Set<String> = emptySet(),
    /** Allowed apps, used in [CountingMode.WHITELIST]. */
    val whitelist: Set<String> = emptySet(),
    /** A counting interval longer than this means the monitor was not running; it's treated as idle. */
    val maxTickGap: Duration = Defaults.maxTickGap,
) {
    init {
        require(levels.isNotEmpty()) { "levels must not be empty" }
        require(levels.all { it.isPositive() }) { "levels must be positive" }
        require(lockoutDuration.isPositive()) { "lockoutDuration must be positive" }
        require(!lockoutGrace.isNegative()) { "lockoutGrace must not be negative" }
        require(streakResetBreak.isPositive()) { "streakResetBreak must be positive" }
        require(escalationResetBreak.isPositive()) { "escalationResetBreak must be positive" }
        require(dailyCap == null || dailyCap.isPositive()) { "dailyCap must be positive" }
        require(warningLead == null || warningLead.isPositive()) { "warningLead must be positive" }
        require(snoozeDuration.isPositive()) { "snoozeDuration must be positive" }
        require(frictionOverrideDuration.isPositive()) { "frictionOverrideDuration must be positive" }
        require(maxTickGap.isPositive()) { "maxTickGap must be positive" }
    }

    val lastLevel: Int get() = levels.lastIndex

    fun threshold(level: Int): Duration = levels[level.coerceIn(levels.indices)]

    val shortestLevel: Duration get() = levels.min()

    object Defaults {
        val levels = listOf(10.minutes, 5.minutes, 2.minutes)
        val lockoutDuration = 15.minutes
        val lockoutGrace = 5.seconds
        val streakResetBreak = 60.seconds
        val escalationResetBreak = 10.minutes

        /** Value offered when the user turns the daily cap on. The cap itself is off by default. */
        val dailyCapWhenEnabled = 2.hours

        val warningLead: Duration? = 1.minutes
        val warningLeadOptions: List<Duration?> = listOf(null, 30.seconds, 1.minutes, 2.minutes)

        val snoozeDuration = 5.minutes
        val frictionWait = 15.seconds
        const val FRICTION_PHRASE = "I choose to be distracted"
        val frictionOverrideDuration = 5.minutes

        val countingMode = CountingMode.SCREEN_ON

        /** How often the monitor ticks while the screen is interactive. */
        val tickInterval = 5.seconds
        val maxTickGap = 30.seconds

        /** Navigation apps that pause counting. User-editable; this is the starting list. */
        val navigationPackages = setOf(
            "com.google.android.apps.maps",
            "com.waze",
            "com.here.app.maps",
            "com.sygic.aura",
            "net.osmand",
            "net.osmand.plus",
            "app.organicmaps",
            "com.mapswithme.maps.pro",
            "ru.yandex.yandexnavi",
            "com.citymapper.app.release",
        )
    }
}
