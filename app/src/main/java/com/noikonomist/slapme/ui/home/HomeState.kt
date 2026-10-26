package com.noikonomist.slapme.ui.home

import com.noikonomist.slapme.data.settings.Settings
import com.noikonomist.slapme.engine.ActivationRules
import com.noikonomist.slapme.engine.UsageState
import com.noikonomist.slapme.monitor.MonitorStatus
import java.time.Instant
import java.time.ZoneId

/** What the home status card shows. */
sealed interface HomeState {
    /** Nothing enabled: nothing will slap you. */
    data object Off : HomeState

    /** Enabled but outside focus time. */
    data class Armed(val nextActivationMillis: Long?) : HomeState

    data class Active(
        val streakMillis: Long,
        val thresholdMillis: Long,
        val level: Int,
        val levelCount: Int,
        val usage: UsageState,
        val snoozedUntilMillis: Long?,
    ) : HomeState

    data class Lockout(val untilMillis: Long, val remainingMillis: Long) : HomeState

    companion object {
        fun from(status: MonitorStatus, rules: ActivationRules, settings: Settings, nowMillis: Long, zone: ZoneId): HomeState {
            val now = Instant.ofEpochMilli(nowMillis)
            if (!rules.isArmed(now)) return Off
            if (!rules.isActive(now, zone)) return Armed(rules.nextActivation(now, zone)?.toEpochMilli())

            status.lockoutUntilMillis?.takeIf { status.running && it > nowMillis }?.let { until ->
                return Lockout(until, until - nowMillis)
            }
            val levels = settings.engine.levels
            if (!status.running) {
                // The monitor hasn't reported yet (just started).
                return Active(0, levels.first().inWholeMilliseconds, 0, levels.size, UsageState.IDLE, null)
            }
            return Active(
                streakMillis = status.streakMillis,
                thresholdMillis = status.thresholdMillis,
                level = status.level,
                levelCount = levels.size,
                usage = status.usage,
                snoozedUntilMillis = status.overrideUntilMillis?.takeIf { it > nowMillis },
            )
        }
    }
}
