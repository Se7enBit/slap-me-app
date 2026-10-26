package com.noikonomist.slapme.ui.schedules

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noikonomist.slapme.R
import com.noikonomist.slapme.engine.DaysMask
import com.noikonomist.slapme.monitor.MonitorStarter
import com.noikonomist.slapme.ui.appContainer
import com.noikonomist.slapme.ui.components.ScreenScaffold
import com.noikonomist.slapme.ui.components.SwitchRow
import java.time.DayOfWeek
import java.time.format.TextStyle
import kotlinx.coroutines.launch

/** Edits block [blockId], or creates one when it's 0. */
@Composable
fun BlockEditorScreen(blockId: Long, onDone: () -> Unit) {
    val blocks by appContainer().scheduleRepository.blocks.collectAsState(initial = null)
    val loaded = blocks ?: return
    val existing = loaded.firstOrNull { it.id == blockId }
    BlockEditor(initial = existing?.let(BlockDraft::from) ?: BlockDraft.NEW, isNew = existing == null, onDone = onDone)
}

private val DraftSaver = listSaver<BlockDraft, Any>(
    save = { listOf(it.id, DaysMask.of(*it.days.toTypedArray()), it.startMinute, it.endMinute, it.allDay, it.enabled) },
    restore = {
        BlockDraft(
            id = it[0] as Long,
            days = DaysMask.days(it[1] as Int),
            startMinute = it[2] as Int,
            endMinute = it[3] as Int,
            allDay = it[4] as Boolean,
            enabled = it[5] as Boolean,
        )
    },
)

private enum class Editing { START, END }

@Composable
private fun BlockEditor(initial: BlockDraft, isNew: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val repository = appContainer().scheduleRepository
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(initial) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    val locale = LocalConfiguration.current.locales[0]

    fun save(block: suspend () -> Unit) {
        scope.launch {
            block()
            MonitorStarter.sync(context)
            onDone()
        }
    }

    ScreenScaffold(
        title = stringResource(if (isNew) R.string.schedule_new_title else R.string.schedule_edit_title),
        onBack = onDone,
        actions = {
            if (!isNew) {
                IconButton(onClick = { save { repository.delete(draft.id) } }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.schedule_delete))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.schedule_days), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (day in DayOfWeek.entries) {
                    FilterChip(
                        selected = day in draft.days,
                        onClick = { draft = draft.copy(days = if (day in draft.days) draft.days - day else draft.days + day) },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                    )
                }
            }

            SwitchRow(stringResource(R.string.schedule_all_day), null, draft.allDay) { draft = draft.copy(allDay = it) }

            if (!draft.allDay) {
                TimeRow(stringResource(R.string.schedule_starts), timeText(draft.startMinute)) { editing = Editing.START }
                TimeRow(stringResource(R.string.schedule_ends), timeText(draft.endMinute)) { editing = Editing.END }
                if (draft.crossesMidnight) {
                    Text(stringResource(R.string.schedule_crosses_midnight), style = MaterialTheme.typography.bodyMedium)
                }
            }

            for (error in draft.errors) {
                Text(
                    stringResource(
                        when (error) {
                            BlockError.NO_DAYS -> R.string.schedule_error_no_days
                            BlockError.SAME_TIMES -> R.string.schedule_error_same_times
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = {
                    val block = draft.toBlock()
                    save { if (isNew) repository.add(block) else repository.update(block) }
                },
                enabled = draft.errors.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.schedule_save)) }
        }
    }

    editing?.let { which ->
        TimeDialog(
            initialMinute = if (which == Editing.START) draft.startMinute else draft.endMinute,
            onConfirm = { minute ->
                draft = if (which == Editing.START) draft.copy(startMinute = minute) else draft.copy(endMinute = minute)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TimeRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onClick) { Text(value) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinute: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = (initialMinute / 60) % 24,
        initialMinute = initialMinute % 60,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
