package com.noikonomist.slapme.ui.override

import com.noikonomist.slapme.data.settings.Settings
import com.noikonomist.slapme.engine.ActivationRules
import com.noikonomist.slapme.engine.FrictionPhrase
import com.noikonomist.slapme.engine.ScheduleBlock
import com.noikonomist.slapme.monitor.MonitorStatus
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration

/** The wait-then-type step of the friction override. */
data class FrictionGate(val startedAtMillis: Long, val waitMillis: Long, val phrase: String) {
    fun secondsLeft(nowMillis: Long): Int =
        ((startedAtMillis + waitMillis - nowMillis).coerceAtLeast(0) + 999).div(1_000).toInt()

    fun unlocked(nowMillis: Long): Boolean = nowMillis >= startedAtMillis + waitMillis

    fun canSubmit(nowMillis: Long, input: String): Boolean = unlocked(nowMillis) && FrictionPhrase.matches(input, phrase)
}

enum class OverridePurpose {
    /** "Need more time?": a snooze or the friction override. */
    MORE_TIME,

    /** Finishing a switch-off that was parked because of a lockout. */
    SWITCH_OFF,
}

sealed interface OverrideChoice {
    data class FreeSnooze(val duration: Duration) : OverrideChoice
    data class Friction(val duration: Duration) : OverrideChoice

    /** Friction, then the parked switch-off is applied. */
    data object SwitchOff : OverrideChoice
    data object NothingToOverride : OverrideChoice
}

object OverrideOptions {
    fun choose(status: MonitorStatus, settings: Settings, purpose: OverridePurpose): OverrideChoice = when {
        purpose == OverridePurpose.SWITCH_OFF -> OverrideChoice.SwitchOff
        !status.running || !status.active -> OverrideChoice.NothingToOverride
        status.freeSnoozeAvailable -> OverrideChoice.FreeSnooze(settings.engine.snoozeDuration)
        else -> OverrideChoice.Friction(settings.engine.frictionOverrideDuration)
    }
}

/** Changes that may stop the app watching. */
sealed interface SwitchOff {
    fun rulesAfter(rules: ActivationRules): ActivationRules

    data object StopSession : SwitchOff {
        override fun rulesAfter(rules: ActivationRules) = rules.copy(session = null)
    }

    data object AlwaysOnOff : SwitchOff {
        override fun rulesAfter(rules: ActivationRules) = rules.copy(alwaysOn = false)
    }

    /** Disabling or editing a block. */
    data class UpdateBlock(val block: ScheduleBlock) : SwitchOff {
        override fun rulesAfter(rules: ActivationRules) =
            rules.copy(blocks = rules.blocks.map { if (it.id == block.id) block else it })
    }

    data class DeleteBlock(val blockId: Long) : SwitchOff {
        override fun rulesAfter(rules: ActivationRules) = rules.copy(blocks = rules.blocks.filter { it.id != blockId })
    }
}

/**
 * During a lockout, a change that would stop the app watching *right now* must go through the friction
 * override, so a lockout can't be escaped with one tap. Everything else stays one tap.
 */
object SwitchOffGate {
    fun needsFriction(action: SwitchOff, rules: ActivationRules, lockoutUntilMillis: Long?, now: Instant, zone: ZoneId): Boolean {
        val lockedOut = lockoutUntilMillis != null && lockoutUntilMillis > now.toEpochMilli()
        return lockedOut && rules.isActive(now, zone) && !action.rulesAfter(rules).isActive(now, zone)
    }
}
