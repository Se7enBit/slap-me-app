package com.noikonomist.slapme.ui.schedules

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noikonomist.slapme.R
import com.noikonomist.slapme.engine.ScheduleBlock
import com.noikonomist.slapme.monitor.MonitorStarter
import com.noikonomist.slapme.ui.appContainer
import com.noikonomist.slapme.ui.components.ScreenScaffold
import kotlinx.coroutines.launch

@Composable
fun SchedulesScreen(onBack: () -> Unit, onEdit: (blockId: Long) -> Unit) {
    val context = LocalContext.current
    val repository = appContainer().scheduleRepository
    val scope = rememberCoroutineScope()
    val blocks by repository.blocks.collectAsState(initial = null)

    ScreenScaffold(
        title = stringResource(R.string.schedules_title),
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(0) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.schedules_add))
            }
        },
    ) { padding ->
        val list = blocks ?: return@ScreenScaffold
        if (list.isEmpty()) {
            Text(
                stringResource(R.string.schedules_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(padding).padding(24.dp),
            )
            return@ScreenScaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(list, key = { it.id }) { block ->
                BlockRow(
                    block = block,
                    onToggle = { enabled ->
                        scope.launch {
                            repository.update(block.copy(enabled = enabled))
                            MonitorStarter.sync(context)
                        }
                    },
                    onClick = { onEdit(block.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BlockRow(block: ScheduleBlock, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(daysLabel(block.daysMask)) },
        supportingContent = { Text(timeRangeLabel(block)) },
        trailingContent = { Switch(checked = block.enabled, onCheckedChange = onToggle) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun daysLabel(mask: Int): String = when (val summary = ScheduleFormat.days(mask)) {
    DaySummary.EveryDay -> stringResource(R.string.days_every_day)
    DaySummary.Weekdays -> stringResource(R.string.days_weekdays)
    DaySummary.Weekend -> stringResource(R.string.days_weekend)
    is DaySummary.Custom -> summary.label(LocalConfiguration.current.locales[0])
}

@Composable
fun timeText(minute: Int): String =
    ScheduleFormat.time(minute, DateFormat.is24HourFormat(LocalContext.current), LocalConfiguration.current.locales[0])

@Composable
private fun timeRangeLabel(block: ScheduleBlock): String {
    if (block.startMinute == 0 && block.endMinute == 24 * 60) return stringResource(R.string.schedule_all_day)
    val range = "${timeText(block.startMinute)}–${timeText(block.endMinute)}"
    return if (block.endMinute < block.startMinute) stringResource(R.string.schedule_next_day, range) else range
}
