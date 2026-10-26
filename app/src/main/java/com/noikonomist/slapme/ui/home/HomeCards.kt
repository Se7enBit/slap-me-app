package com.noikonomist.slapme.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noikonomist.slapme.R
import com.noikonomist.slapme.engine.FocusSession
import com.noikonomist.slapme.engine.UsageState
import com.noikonomist.slapme.ui.appContainer
import com.noikonomist.slapme.ui.components.CardColumn
import com.noikonomist.slapme.ui.components.SwitchRow
import com.noikonomist.slapme.ui.components.clockText
import com.noikonomist.slapme.ui.components.durationText
import com.noikonomist.slapme.ui.components.timeOfDayText
import com.noikonomist.slapme.ui.components.upcomingText
import com.noikonomist.slapme.ui.settings.SettingOptions
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun StatusCard(state: HomeState) {
    val container = when (state) {
        is HomeState.Lockout -> MaterialTheme.colorScheme.errorContainer
        is HomeState.Active -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth()) {
        CardColumn {
            when (state) {
                HomeState.Off -> {
                    Title(stringResource(R.string.home_off_title))
                    Text(stringResource(R.string.home_off_text))
                }

                is HomeState.Armed -> {
                    Title(stringResource(R.string.home_armed_title))
                    Text(
                        state.nextActivationMillis?.let { stringResource(R.string.home_armed_next, upcomingText(it)) }
                            ?: stringResource(R.string.home_armed_waiting),
                    )
                }

                is HomeState.Active -> {
                    Title(stringResource(R.string.home_active_title))
                    LinearProgressIndicator(
                        progress = { (state.streakMillis.toFloat() / state.thresholdMillis.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(
                            R.string.home_active_streak,
                            clockText(state.streakMillis),
                            durationText(state.thresholdMillis.milliseconds),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.home_active_level, state.level + 1, state.levelCount))
                    Text(
                        stringResource(
                            when (state.usage) {
                                UsageState.COUNTING -> R.string.home_usage_counting
                                UsageState.PAUSED -> R.string.home_usage_paused
                                UsageState.IDLE -> R.string.home_usage_idle
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    state.snoozedUntilMillis?.let {
                        Text(stringResource(R.string.home_snoozed_until, timeOfDayText(it)), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is HomeState.Lockout -> {
                    Title(stringResource(R.string.home_lockout_title))
                    Text(clockText(state.remainingMillis), style = MaterialTheme.typography.displaySmall)
                    Text(stringResource(R.string.home_lockout_text))
                }
            }
        }
    }
}

@Composable
fun SessionCard(session: FocusSession?, nowMillis: Long, onStart: (Duration?) -> Unit, onStop: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        CardColumn {
            if (session != null) {
                Title(stringResource(R.string.home_session_running))
                Text(
                    session.plannedEndMillis?.let { stringResource(R.string.home_session_left, clockText(it - nowMillis)) }
                        ?: stringResource(R.string.home_session_open),
                )
                OutlinedButton(onClick = onStop) { Text(stringResource(R.string.home_session_stop)) }
            } else {
                Title(stringResource(R.string.home_session_start))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (length in SettingOptions.sessionLengths) {
                        OutlinedButton(onClick = { onStart(length) }) {
                            Text(length?.let { durationText(it) } ?: stringResource(R.string.home_session_until_stopped))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModesCard(alwaysOn: Boolean, blockCount: Int, onAlwaysOn: (Boolean) -> Unit, onOpenSchedules: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        CardColumn {
            Title(stringResource(R.string.home_modes_title))
            SwitchRow(stringResource(R.string.settings_always_on), stringResource(R.string.settings_always_on_text), alwaysOn, onAlwaysOn)
            Column(Modifier.fillMaxWidth().clickable(onClick = onOpenSchedules).padding(vertical = 8.dp)) {
                Text(stringResource(R.string.schedules_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (blockCount == 0) stringResource(R.string.home_schedules_none) else pluralStringResource(R.plurals.home_schedules_count, blockCount, blockCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun TodayCard() {
    val history = appContainer().historyRepository
    val zone = ZoneId.systemDefault()
    val today = remember { LocalDate.now(zone) }
    val from = remember(today) { today.atStartOfDay(zone).toInstant().toEpochMilli() }
    val to = remember(today) { today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val usage by remember(today) { history.dailyUsage(today, today) }.collectAsState(initial = emptyList())
    val slaps by remember(from) { history.slaps(from, to) }.collectAsState(initial = emptyList())
    val overrides by remember(from) { history.overrides(from, to) }.collectAsState(initial = emptyList())
    val stats = usage.firstOrNull()

    Card(Modifier.fillMaxWidth()) {
        CardColumn {
            Title(stringResource(R.string.home_today_title))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(durationText((stats?.countedMillis ?: 0).milliseconds), stringResource(R.string.home_today_used))
                Stat(slaps.size.toString(), stringResource(R.string.home_today_slaps))
                Stat(durationText((stats?.longestFocusMillis ?: 0).milliseconds), stringResource(R.string.home_today_focus))
                Stat(overrides.size.toString(), stringResource(R.string.home_today_overrides))
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Title(text: String, color: Color = Color.Unspecified) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = color)
}
