package com.noikonomist.slapme.ui.settings

import com.noikonomist.slapme.data.settings.Settings
import com.noikonomist.slapme.engine.EngineConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Editing the escalation ladder. */
object LevelsEditor {
    const val MAX_LEVELS = 5
    val range = 1.minutes..120.minutes

    /** A new level at half the last one (whole minutes, at least 1 min). */
    fun add(levels: List<Duration>): List<Duration> {
        if (levels.size >= MAX_LEVELS) return levels
        val half = (levels.last() / 2).inWholeMinutes.minutes
        return levels + maxOf(half, range.start)
    }

    fun remove(levels: List<Duration>, index: Int): List<Duration> =
        if (levels.size <= 1) levels else levels.filterIndexed { i, _ -> i != index }

    fun set(levels: List<Duration>, index: Int, value: Duration): List<Duration> =
        levels.mapIndexed { i, level -> if (i == index) value else level }
}

/** −/+ steppers move along a grid; off-grid values (debug seconds) snap onto it first. */
object Stepper {
    fun step(value: Duration, delta: Int, step: Duration, range: ClosedRange<Duration>): Duration {
        val steps = value / step
        val target = when {
            steps % 1.0 == 0.0 -> value + step * delta
            delta > 0 -> step * kotlin.math.ceil(steps)
            else -> step * kotlin.math.floor(steps)
        }
        return target.coerceIn(range.start, range.endInclusive)
    }
}

object SettingOptions {
    val warningLeads: List<Duration?> = EngineConfig.Defaults.warningLeadOptions
    val streakBreaks = listOf(30.seconds, 1.minutes, 2.minutes, 5.minutes)
    val escalationBreaks = listOf(5.minutes, 10.minutes, 15.minutes, 30.minutes)
    val sessionLengths: List<Duration?> = listOf(25.minutes, 50.minutes, 90.minutes, null)

    val lockoutRange = 5.minutes..60.minutes
    val lockoutStep = 5.minutes
    val graceRange = 0.seconds..30.seconds
    val graceStep = 5.seconds
    val dailyCapRange = 30.minutes..8.hours
    val dailyCapStep = 30.minutes
    val dailyCapWhenTurnedOn = EngineConfig.Defaults.dailyCapWhenEnabled
}

/** The three app lists the picker edits. */
enum class AppListKind {
    DISTRACTING,
    ALLOWED,
    NAVIGATION,
    ;

    fun selected(settings: Settings): Set<String> = when (this) {
        DISTRACTING -> settings.engine.blacklist
        ALLOWED -> settings.engine.whitelist
        NAVIGATION -> settings.navigationPackages
    }

    fun withSelected(settings: Settings, packages: Set<String>): Settings = when (this) {
        DISTRACTING -> settings.copy(engine = settings.engine.copy(blacklist = packages))
        ALLOWED -> settings.copy(engine = settings.engine.copy(whitelist = packages))
        NAVIGATION -> settings.copy(navigationPackages = packages)
    }
}
