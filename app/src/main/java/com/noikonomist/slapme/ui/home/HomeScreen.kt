package com.noikonomist.slapme.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import java.time.ZoneId
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onOpenSchedules: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val status by container.monitorStatus.collectAsState()
    val rules by container.activationRulesRepository.rules.collectAsState(initial = null)
    val settings by container.settingsRepository.settings.collectAsState(initial = null)
    val session by container.focusSessionRepository.running.collectAsState(initial = null)
    val now by rememberNow()

    /** Any change that may arm or disarm: start or stop the monitor right away. */
    fun change(block: suspend () -> Unit) {
        scope.launch {
            block()
            MonitorStarter.sync(context)
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.app_name),
        onBack = null,
        actions = {
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
                StatusCard(HomeState.from(status, currentRules, currentSettings, now, ZoneId.systemDefault()))
                SessionCard(
                    session = session,
                    nowMillis = now,
                    onStart = { length -> change { container.focusSessionRepository.start(length) } },
                    onStop = { change { container.focusSessionRepository.stop() } },
                )
                ModesCard(
                    alwaysOn = currentSettings.alwaysOn,
                    blockCount = currentRules.blocks.size,
                    onAlwaysOn = { on -> change { container.settingsRepository.update { it.copy(alwaysOn = on) } } },
                    onOpenSchedules = onOpenSchedules,
                )
                TodayCard()
            }
            DebugPanel()
        }
    }
}
