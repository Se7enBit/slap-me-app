package com.noikonomist.slapme.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noikonomist.slapme.R
import com.noikonomist.slapme.debug.DebugPanel
import com.noikonomist.slapme.monitor.MonitorStarter
import com.noikonomist.slapme.ui.appContainer
import com.noikonomist.slapme.ui.components.ScreenScaffold
import com.noikonomist.slapme.ui.components.rememberNow
import com.noikonomist.slapme.ui.override.SwitchOff
import com.noikonomist.slapme.ui.override.rememberSwitchOffGuard
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenSchedules: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onNeedMoreTime: () -> Unit,
    onSwitchOffParked: () -> Unit,
) {
    val context = LocalContext.current
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val status by container.monitorStatus.collectAsState()
    val rules by container.activationRulesRepository.rules.collectAsState(initial = null)
    val settings by container.settingsRepository.settings.collectAsState(initial = null)
    val session by container.focusSessionRepository.running.collectAsState(initial = null)
    val now by rememberNow()
    val guard = rememberSwitchOffGuard()

    /** Any change that may arm or disarm: start or stop the monitor right away. */
    fun change(block: suspend () -> Unit) {
        scope.launch {
            block()
            MonitorStarter.sync(context)
        }
    }

    /** Switch-offs may need the friction override during a lockout. */
    fun switchOff(action: SwitchOff) {
        scope.launch { if (guard.attempt(action)) onSwitchOffParked() }
    }

    ScreenScaffold(
        title = stringResource(R.string.app_name),
        onBack = null,
        actions = {
            IconButton(onClick = onOpenStats) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.stats_title))
            }
            IconButton(onClick = onOpenSchedules) {
                Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.schedules_title))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FixItCards(Modifier.fillMaxWidth())
            val currentRules = rules
            val currentSettings = settings
            if (currentRules != null && currentSettings != null) {
                StatusCard(HomeState.from(status, currentRules, currentSettings, now, ZoneId.systemDefault()), onNeedMoreTime)
                SessionCard(
                    session = session,
                    nowMillis = now,
                    onStart = { length -> change { container.focusSessionRepository.start(length) } },
                    onStop = { switchOff(SwitchOff.StopSession) },
                )
                ModesCard(
                    alwaysOn = currentSettings.alwaysOn,
                    blockCount = currentRules.blocks.size,
                    onAlwaysOn = { on ->
                        if (on) change { container.settingsRepository.update { it.copy(alwaysOn = true) } } else switchOff(SwitchOff.AlwaysOnOff)
                    },
                    onOpenSchedules = onOpenSchedules,
                )
                TodayCard(onClick = onOpenStats)
            }
            DebugPanel()
        }
    }
}
