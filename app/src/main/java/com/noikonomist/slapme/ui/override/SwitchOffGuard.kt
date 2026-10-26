package com.noikonomist.slapme.ui.override

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.noikonomist.slapme.AppContainer
import com.noikonomist.slapme.engine.EngineEffect
import com.noikonomist.slapme.engine.OverrideType
import com.noikonomist.slapme.monitor.MonitorStarter
import com.noikonomist.slapme.ui.appContainer
import com.noikonomist.slapme.util.SlapLog
import kotlin.time.Duration
import kotlinx.coroutines.flow.first

/** Applies switch-offs, parking them behind the friction override when a lockout requires it. */
class SwitchOffGuard(private val context: Context, private val container: AppContainer) {

    /** Applies [action] now, or parks it and returns true when the friction override must come first. */
    suspend fun attempt(action: SwitchOff): Boolean {
        val rules = container.activationRulesRepository.rules.first()
        val lockout = container.monitorStatus.value.lockoutUntilMillis
        if (SwitchOffGate.needsFriction(action, rules, lockout, container.clock.instant(), container.clock.zone)) {
            SlapLog.i("Switch-off during lockout needs the friction override: $action")
            container.pendingSwitchOff = action
            return true
        }
        apply(action)
        return false
    }

    /** Applies the parked switch-off after the friction flow and logs it. False if nothing was parked. */
    suspend fun completePending(): Boolean {
        val action = container.pendingSwitchOff ?: return false
        container.pendingSwitchOff = null
        apply(action)
        container.effectRecorder.record(
            listOf(EngineEffect.LogOverride(OverrideType.SWITCH_OFF, Duration.ZERO)),
            container.clock.millis(),
        )
        SlapLog.i("Switch-off applied after the friction override: $action")
        return true
    }

    private suspend fun apply(action: SwitchOff) {
        when (action) {
            SwitchOff.StopSession -> container.focusSessionRepository.stop()
            SwitchOff.AlwaysOnOff -> container.settingsRepository.update { it.copy(alwaysOn = false) }
            is SwitchOff.UpdateBlock -> container.scheduleRepository.update(action.block)
            is SwitchOff.DeleteBlock -> container.scheduleRepository.delete(action.blockId)
        }
        MonitorStarter.sync(context)
    }
}

@Composable
fun rememberSwitchOffGuard(): SwitchOffGuard {
    val context = LocalContext.current.applicationContext
    val container = appContainer()
    return remember { SwitchOffGuard(context, container) }
}
