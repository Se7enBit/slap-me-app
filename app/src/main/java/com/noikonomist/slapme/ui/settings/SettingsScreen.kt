package com.noikonomist.slapme.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noikonomist.slapme.R
import com.noikonomist.slapme.data.settings.Settings
import com.noikonomist.slapme.engine.CountingMode
import com.noikonomist.slapme.monitor.MonitorStarter
import com.noikonomist.slapme.ui.appContainer
import com.noikonomist.slapme.ui.components.OptionsRow
import com.noikonomist.slapme.ui.components.ScreenScaffold
import com.noikonomist.slapme.ui.components.SectionHeader
import com.noikonomist.slapme.ui.components.StepperRow
import com.noikonomist.slapme.ui.components.SwitchRow
import com.noikonomist.slapme.ui.components.durationText
import com.noikonomist.slapme.ui.apps.AppListFilter
import com.noikonomist.slapme.ui.apps.InstalledApps
import com.noikonomist.slapme.ui.override.SwitchOff
import com.noikonomist.slapme.ui.override.rememberSwitchOffGuard
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch

/** Every setting saves immediately; a running monitor picks it up live. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onPickApps: (AppListKind) -> Unit, onSwitchOffParked: () -> Unit) {
    val context = LocalContext.current
    val repository = appContainer().settingsRepository
    val scope = rememberCoroutineScope()
    val loaded by repository.settings.collectAsState(initial = null)
    val guard = rememberSwitchOffGuard()
    // Counts show only apps that are installed; the saved lists keep the rest.
    val installed by produceState<Set<String>?>(null) {
        value = withContext(Dispatchers.IO) { InstalledApps.launcherApps(context).mapTo(mutableSetOf()) { it.packageName } }
    }
    fun count(apps: Set<String>) = installed?.let { AppListFilter.installedCount(apps, it) } ?: apps.size

    fun update(transform: (Settings) -> Settings) {
        scope.launch {
            repository.update(transform)
            MonitorStarter.sync(context)
        }
    }

    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        val settings = loaded ?: return@ScreenScaffold
        val engine = settings.engine
        fun updateEngine(transform: (com.noikonomist.slapme.engine.EngineConfig) -> com.noikonomist.slapme.engine.EngineConfig) =
            update { it.copy(engine = transform(it.engine)) }

        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Activation
            SectionHeader(stringResource(R.string.settings_section_activation))
            SwitchRow(stringResource(R.string.settings_always_on), stringResource(R.string.settings_always_on_text), settings.alwaysOn) { on ->
                if (on) update { it.copy(alwaysOn = true) } else scope.launch { if (guard.attempt(SwitchOff.AlwaysOnOff)) onSwitchOffParked() }
            }
            SwitchRow(stringResource(R.string.settings_daily_cap), stringResource(R.string.settings_daily_cap_text), engine.dailyCap != null) { on ->
                updateEngine { it.copy(dailyCap = if (on) SettingOptions.dailyCapWhenTurnedOn else null) }
            }
            engine.dailyCap?.let { cap ->
                DurationStepper(stringResource(R.string.settings_daily_cap_value), cap, SettingOptions.dailyCapStep, SettingOptions.dailyCapRange) { v ->
                    updateEngine { it.copy(dailyCap = v) }
                }
            }

            // What counts
            SectionHeader(stringResource(R.string.settings_section_counting))
            ModeRow(stringResource(R.string.settings_mode_screen_on), engine.countingMode == CountingMode.SCREEN_ON) {
                updateEngine { it.copy(countingMode = CountingMode.SCREEN_ON) }
            }
            ModeRow(
                stringResource(R.string.settings_mode_blacklist),
                engine.countingMode == CountingMode.BLACKLIST,
                apps = count(engine.blacklist),
                onChooseApps = { onPickApps(AppListKind.DISTRACTING) },
            ) { updateEngine { it.copy(countingMode = CountingMode.BLACKLIST) } }
            ModeRow(
                stringResource(R.string.settings_mode_whitelist),
                engine.countingMode == CountingMode.WHITELIST,
                apps = count(engine.whitelist),
                onChooseApps = { onPickApps(AppListKind.ALLOWED) },
            ) { updateEngine { it.copy(countingMode = CountingMode.WHITELIST) } }
            Note(stringResource(R.string.settings_counting_note))

            // Limits
            SectionHeader(stringResource(R.string.settings_section_limits))
            Note(stringResource(R.string.settings_limits_note))
            engine.levels.forEachIndexed { i, level ->
                DurationStepper(
                    title = if (i == 0) stringResource(R.string.settings_level_first) else stringResource(R.string.settings_level, i + 1),
                    value = level,
                    step = 1.minutes,
                    range = LevelsEditor.range,
                    trailing = {
                        if (engine.levels.size > 1) {
                            IconButton(onClick = { updateEngine { it.copy(levels = LevelsEditor.remove(it.levels, i)) } }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.settings_level_remove))
                            }
                        }
                    },
                ) { v -> updateEngine { it.copy(levels = LevelsEditor.set(it.levels, i, v)) } }
            }
            if (engine.levels.size < LevelsEditor.MAX_LEVELS) {
                TextButton(onClick = { updateEngine { it.copy(levels = LevelsEditor.add(it.levels)) } }) {
                    Text(stringResource(R.string.settings_level_add))
                }
            }

            // Breaks
            SectionHeader(stringResource(R.string.settings_section_breaks))
            OptionsRow(stringResource(R.string.settings_streak_break), SettingOptions.streakBreaks, engine.streakResetBreak, { durationText(it) }) { v ->
                updateEngine { it.copy(streakResetBreak = v) }
            }
            OptionsRow(stringResource(R.string.settings_escalation_break), SettingOptions.escalationBreaks, engine.escalationResetBreak, { durationText(it) }) { v ->
                updateEngine { it.copy(escalationResetBreak = v) }
            }

            // Lockout
            SectionHeader(stringResource(R.string.settings_section_lockout))
            DurationStepper(stringResource(R.string.settings_lockout_length), engine.lockoutDuration, SettingOptions.lockoutStep, SettingOptions.lockoutRange) { v ->
                updateEngine { it.copy(lockoutDuration = v) }
            }
            DurationStepper(stringResource(R.string.settings_lockout_grace), engine.lockoutGrace, SettingOptions.graceStep, SettingOptions.graceRange) { v ->
                updateEngine { it.copy(lockoutGrace = v) }
            }

            // Warning
            SectionHeader(stringResource(R.string.settings_section_warning))
            OptionsRow(
                stringResource(R.string.settings_warning),
                SettingOptions.warningLeads,
                engine.warningLead,
                { lead -> lead?.let { durationText(it) } ?: stringResource(R.string.settings_warning_off) },
            ) { v -> updateEngine { it.copy(warningLead = v) } }

            // Sound
            SectionHeader(stringResource(R.string.settings_section_sound))
            SwitchRow(stringResource(R.string.settings_always_play), stringResource(R.string.settings_always_play_text), settings.alwaysPlaySound) { on ->
                update { it.copy(alwaysPlaySound = on) }
            }

            // Navigation apps
            SectionHeader(stringResource(R.string.settings_section_navigation))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.settings_navigation_text, count(settings.navigationPackages), count(settings.navigationPackages)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onPickApps(AppListKind.NAVIGATION) }) { Text(stringResource(R.string.settings_choose_apps)) }
            }
        }
    }
}

@Composable
private fun DurationStepper(
    title: String,
    value: Duration,
    step: Duration,
    range: ClosedRange<Duration>,
    trailing: @Composable () -> Unit = {},
    onChange: (Duration) -> Unit,
) {
    StepperRow(
        title = title,
        value = durationText(value),
        onMinus = { onChange(Stepper.step(value, -1, step, range)) },
        onPlus = { onChange(Stepper.step(value, +1, step, range)) },
        minusEnabled = value > range.start,
        plusEnabled = value < range.endInclusive,
        trailing = trailing,
    )
}

@Composable
private fun ModeRow(label: String, selected: Boolean, apps: Int? = null, onChooseApps: (() -> Unit)? = null, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            apps?.let { Text(pluralStringResource(R.plurals.settings_apps_chosen, it, it), style = MaterialTheme.typography.bodySmall) }
        }
        onChooseApps?.let { TextButton(onClick = it) { Text(stringResource(R.string.settings_choose_apps)) } }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
