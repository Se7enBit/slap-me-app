package com.noikonomist.slapme.engine

enum class UsageState {
    /** Time adds to the current streak. */
    COUNTING,

    /** An exception is in the foreground: streak and idle run are frozen. */
    PAUSED,

    /** Not using the phone (or not using a tracked app). Long enough idle is a break. */
    IDLE,
}

/** What is in the foreground, already resolved by the monitor (which knows about packages). */
sealed interface Foreground {
    /** No foreground event seen yet. */
    data object Unknown : Foreground

    data object Launcher : Foreground

    /** Navigation, camera or Slap Me itself. */
    data class Exempt(val packageName: String) : Foreground

    data class App(val packageName: String) : Foreground
}

/** One snapshot of the device, as reported by the monitor on each tick or screen change. */
data class Observation(
    val interactive: Boolean,
    val unlocked: Boolean,
    val inCall: Boolean,
    val foreground: Foreground,
)

object UsageClassifier {

    fun classify(observation: Observation, config: EngineConfig): UsageState = with(observation) {
        when {
            inCall -> UsageState.PAUSED
            !interactive || !unlocked -> UsageState.IDLE
            foreground is Foreground.Launcher -> UsageState.IDLE
            foreground is Foreground.Exempt -> UsageState.PAUSED
            else -> byCountingMode(foreground, config)
        }
    }

    private fun byCountingMode(foreground: Foreground, config: EngineConfig): UsageState {
        val pkg = (foreground as? Foreground.App)?.packageName
        val counts = when (config.countingMode) {
            CountingMode.SCREEN_ON -> true
            CountingMode.BLACKLIST -> pkg != null && pkg in config.blacklist
            CountingMode.WHITELIST -> pkg == null || pkg !in config.whitelist
        }
        return if (counts) UsageState.COUNTING else UsageState.IDLE
    }
}
