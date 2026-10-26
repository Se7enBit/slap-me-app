package com.noikonomist.slapme.ui.override

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.noikonomist.slapme.R
import com.noikonomist.slapme.engine.EngineConfig
import com.noikonomist.slapme.engine.OverrideRejection
import com.noikonomist.slapme.monitor.OverrideResult
import com.noikonomist.slapme.ui.appContainer
import com.noikonomist.slapme.ui.components.ScreenScaffold
import com.noikonomist.slapme.ui.components.clockText
import com.noikonomist.slapme.ui.components.durationText
import com.noikonomist.slapme.ui.components.rememberNow
import kotlinx.coroutines.launch

/**
 * "Need more time?": the free snooze while it lasts, then the friction override (wait 15 s, type the
 * phrase). Also finishes a switch-off that a lockout parked. Slap Me itself never counts, so being on
 * this screen is never slapped.
 */
@Composable
fun OverrideScreen(purpose: OverridePurpose, onDone: () -> Unit, onBack: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val guard = rememberSwitchOffGuard()
    val status by container.monitorStatus.collectAsState()
    val settings by container.settingsRepository.settings.collectAsState(initial = null)
    val now by rememberNow()
    var problem by remember { mutableStateOf<Int?>(null) }

    fun handle(result: OverrideResult) {
        problem = when (result) {
            is OverrideResult.Granted -> {
                onDone()
                null
            }
            is OverrideResult.Rejected -> when (result.reason) {
                OverrideRejection.FREE_SNOOZE_USED -> R.string.override_snooze_used
                OverrideRejection.NOT_ACTIVE -> R.string.override_nothing
            }
            OverrideResult.NotRunning -> R.string.override_nothing
        }
    }

    val title = if (purpose == OverridePurpose.SWITCH_OFF) R.string.override_switch_off_title else R.string.override_title
    ScreenScaffold(title = stringResource(title), onBack = onBack) { padding ->
        val current = settings ?: return@ScreenScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            status.lockoutUntilMillis?.takeIf { it > now }?.let {
                Text(stringResource(R.string.override_lockout_left, clockText(it - now)), color = MaterialTheme.colorScheme.error)
            }

            when (val choice = OverrideOptions.choose(status, current, purpose)) {
                is OverrideChoice.FreeSnooze -> {
                    Text(stringResource(R.string.override_free_snooze_text), style = MaterialTheme.typography.bodyLarge)
                    Button(
                        onClick = { scope.launch { handle(container.monitorBridge.snooze()) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.override_free_snooze_button, durationText(choice.duration))) }
                }

                is OverrideChoice.Friction -> {
                    Text(stringResource(R.string.override_friction_text), style = MaterialTheme.typography.bodyLarge)
                    FrictionPanel(stringResource(R.string.override_friction_button, durationText(choice.duration))) {
                        scope.launch { handle(container.monitorBridge.grantFrictionOverride()) }
                    }
                }

                OverrideChoice.SwitchOff -> {
                    Text(stringResource(R.string.override_switch_off_text), style = MaterialTheme.typography.bodyLarge)
                    FrictionPanel(stringResource(R.string.override_switch_off_button)) {
                        scope.launch { if (guard.completePending()) onDone() else problem = R.string.override_nothing_pending }
                    }
                }

                OverrideChoice.NothingToOverride -> {
                    Text(stringResource(R.string.override_nothing), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                }
            }

            problem?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Wait, then type the phrase. The wait restarts whenever the screen comes back. */
@Composable
private fun FrictionPanel(confirmLabel: String, onConfirmed: () -> Unit) {
    val phrase = stringResource(R.string.friction_phrase)
    var startedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        startedAt = System.currentTimeMillis()
        onPauseOrDispose { }
    }
    var input by rememberSaveable { mutableStateOf("") }
    val now by rememberNow()
    val gate = FrictionGate(startedAt, EngineConfig.Defaults.frictionWait.inWholeMilliseconds, phrase)

    if (!gate.unlocked(now)) {
        Text(gate.secondsLeft(now).toString(), style = MaterialTheme.typography.displayLarge)
        Text(stringResource(R.string.friction_wait_text), style = MaterialTheme.typography.bodyLarge)
        return
    }
    Text(stringResource(R.string.friction_type_this), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.friction_phrase_quoted, phrase), style = MaterialTheme.typography.titleLarge)
    // No copy/paste menu: the point is to type it.
    CompositionLocalProvider(LocalTextToolbar provides NoTextToolbar) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Button(onClick = onConfirmed, enabled = gate.canSubmit(now, input), modifier = Modifier.fillMaxWidth()) {
        Text(confirmLabel)
    }
}

private object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden

    override fun hide() = Unit

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit
}
