package com.noikonomist.slapme.engine

import kotlin.time.Duration

enum class SlapReason { THRESHOLD, LOCKOUT, DAILY_CAP }

enum class OverrideType { SNOOZE, FRICTION }

enum class OverrideRejection {
    /** Nothing is active, so there is nothing to override. */
    NOT_ACTIVE,

    /** The free snooze of this period is used up; the friction override is still available. */
    FREE_SNOOZE_USED,
}

/** What the monitor must do after an event. The engine never does any of it itself. */
sealed interface EngineEffect {
    data class Warn(val slapAtMillis: Long, val freeSnoozeAvailable: Boolean) : EngineEffect

    data object CancelWarning : EngineEffect

    /** Supersedes any pending warning. [level] is the level the slap happened at. */
    data class Slap(val reason: SlapReason, val level: Int, val packageName: String?) : EngineEffect

    data class StartLockout(val untilMillis: Long) : EngineEffect

    data object EndLockout : EngineEffect

    data class LogOverride(val type: OverrideType, val duration: Duration) : EngineEffect

    data class OverrideRejected(val reason: OverrideRejection) : EngineEffect

    /** A planned focus session reached its end; record `actualEnd` and drop it from the rules. */
    data class SessionExpired(val sessionId: Long) : EngineEffect

    /** Final numbers of the day that just ended. */
    data class DayRolledOver(val stats: DailyStats) : EngineEffect

    /** Counting stopped being reported for this long (monitor killed?). The gap was treated as idle. */
    data class ServiceGap(val duration: Duration) : EngineEffect
}
