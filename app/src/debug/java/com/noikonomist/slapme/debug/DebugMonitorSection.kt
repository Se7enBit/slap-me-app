package com.noikonomist.slapme.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.noikonomist.slapme.R
import com.noikonomist.slapme.SlapMeApp
import com.noikonomist.slapme.data.settings.Settings
import com.noikonomist.slapme.engine.EngineConfig
import com.noikonomist.slapme.monitor.MonitorStarter
import com.noikonomist.slapme.monitor.MonitorStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch

/** Timings in seconds, so warning → slap → escalation → lockout takes a few minutes. */
object DebugPresets {
    fun fast(settings: Settings) = settings.copy(
        engine = settings.engine.copy(
            levels = listOf(30.seconds, 15.seconds, 10.seconds),
            lockoutDuration = 60.seconds,
            lockoutGrace = 5.seconds,
            streakResetBreak = 15.seconds,
            escalationResetBreak = 60.seconds,
            warningLead = 10.seconds,
            snoozeDuration = 20.seconds,
            frictionOverrideDuration = 20.seconds,
        ),
    )

    /** Timings back to the defaults; mode, lists and cap stay. */
    fun defaults(settings: Settings) = settings.copy(
        engine = EngineConfig(
            dailyCap = settings.engine.dailyCap,
            countingMode = settings.engine.countingMode,
            blacklist = settings.engine.blacklist,
            whitelist = settings.engine.whitelist,
        ),
    )
}

@Composable
fun DebugMonitorSection() {
    val context = LocalContext.current
    val container = (context.applicationContext as SlapMeApp).container
    val scope = rememberCoroutineScope()
    val settings by container.settingsRepository.settings.collectAsState(initial = null)
    val session by container.focusSessionRepository.running.collectAsState(initial = null)
    val status by container.monitorStatus.collectAsState()

    fun change(block: suspend () -> Unit) {
        scope.launch {
            block()
            MonitorStarter.sync(context)
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.debug_monitor_title), style = MaterialTheme.typography.titleSmall)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.debug_always_on), Modifier.weight(1f))
            Switch(
                checked = settings?.alwaysOn == true,
                onCheckedChange = { on -> change { container.settingsRepository.update { it.copy(alwaysOn = on) } } },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { change { container.focusSessionRepository.start(2.minutes) } }) {
                Text(stringResource(R.string.debug_start_session))
            }
            OutlinedButton(onClick = { change { container.focusSessionRepository.stop() } }, enabled = session != null) {
                Text(stringResource(R.string.debug_stop_session))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { change { container.settingsRepository.update(DebugPresets::fast) } }) {
                Text(stringResource(R.string.debug_fast_thresholds))
            }
            OutlinedButton(onClick = { change { container.settingsRepository.update(DebugPresets::defaults) } }) {
                Text(stringResource(R.string.debug_default_thresholds))
            }
        }
        Text(
            describe(status, settings),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun describe(status: MonitorStatus, settings: Settings?): String {
    val levels = settings?.engine?.levels?.joinToString(" → ") { it.toString() } ?: "?"
    if (!status.running) return "monitor: not running\nlevels: $levels"
    val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
    return buildString {
        appendLine("monitor: running, armed=${status.armed}, active=${status.active}")
        appendLine("usage: ${status.usage} ${status.foregroundPackage ?: ""}")
        appendLine("streak: ${status.streakMillis / 1000}s / ${status.thresholdMillis / 1000}s, level ${status.level}")
        status.lockoutUntilMillis?.let { appendLine("lockout until ${time.format(Date(it))}") }
        status.overrideUntilMillis?.let { appendLine("override until ${time.format(Date(it))}") }
        append("levels: $levels")
    }
}
