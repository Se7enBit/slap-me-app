package com.noikonomist.slapme.engine

import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Today's numbers, written to `daily_usage` when the day rolls over. */
data class DailyStats(
    val date: LocalDate,
    /** Counting time while active. */
    val countedMillis: Long = 0,
    /** Longest stretch of active time without a slap. */
    val longestFocusMillis: Long = 0,
)

/**
 * Everything the engine remembers between events. Plain values only, so it can be saved as-is and
 * restored after a process death or reboot. Times are epoch millis.
 */
data class EngineState(
    /** When the last event was handled. Null before the very first event. */
    val lastEventAtMillis: Long? = null,
    /** Whether the app was active at the last event. */
    val active: Boolean = false,
    /** Usage state reported at the last event; it applies until the next one. */
    val usage: UsageState = UsageState.IDLE,
    val foregroundPackage: String? = null,
    val streakMillis: Long = 0,
    /** Continuous idle time; paused time neither adds to it nor resets it. */
    val idleRunMillis: Long = 0,
    val level: Int = 0,
    val lockoutUntilMillis: Long? = null,
    /** Continuous counting time during lockout, compared with the lockout grace. */
    val lockoutCountingMillis: Long = 0,
    /** Slaps are held back until this time (snooze or friction override). */
    val overrideUntilMillis: Long? = null,
    val warningIssued: Boolean = false,
    /** [ActivePeriod.key]s whose free snooze has been used. */
    val usedSnoozePeriods: Set<String> = emptySet(),
    val today: DailyStats? = null,
    /** Current stretch of active time without a slap. */
    val focusRunMillis: Long = 0,
    /** Planned session whose expiry was already reported, so it is reported once. */
    val reportedExpiredSessionId: Long? = null,
) {
    val inLockout: Boolean get() = lockoutUntilMillis != null
    val streak: Duration get() = streakMillis.milliseconds
}
