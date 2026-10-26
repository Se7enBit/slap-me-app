package com.noikonomist.slapme.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noikonomist.slapme.R
import com.noikonomist.slapme.data.settings.OnboardingState
import com.noikonomist.slapme.permissions.OnboardingStep
import com.noikonomist.slapme.permissions.PermissionChecklist
import com.noikonomist.slapme.permissions.PermissionIntents
import com.noikonomist.slapme.permissions.PermissionStatus
import com.noikonomist.slapme.ui.appContainer
import com.noikonomist.slapme.ui.rememberPermissions
import kotlinx.coroutines.launch

/** First-run walkthrough. Every permission step can be skipped; whatever is missing shows up on Home. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val steps = remember { PermissionChecklist.onboardingSteps(Build.VERSION.SDK_INT) }
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }
    val onboarding by container.onboardingRepository.state.collectAsState(initial = OnboardingState())
    val permissions = rememberPermissions()
    val granted = permissions.state
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissions.refresh()
    }

    fun next(disclosureAccepted: Boolean = onboarding.usageDisclosureAccepted) {
        val upcoming = PermissionChecklist.nextStep(step, steps, disclosureAccepted)
        if (upcoming == null) onFinished() else step = upcoming
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            StepDots(current = steps.indexOf(step), count = steps.size)
            Spacer(Modifier.height(24.dp))
            when (step) {
                OnboardingStep.WELCOME -> Step(
                    title = R.string.onboarding_welcome_title,
                    primary = R.string.onboarding_get_started to { next() },
                ) {
                    Image(
                        painterResource(R.drawable.slap_hand),
                        contentDescription = null,
                        modifier = Modifier.size(140.dp).align(Alignment.CenterHorizontally),
                    )
                    Bullet(R.string.onboarding_welcome_1)
                    Bullet(R.string.onboarding_welcome_2)
                    Bullet(R.string.onboarding_welcome_3)
                }

                OnboardingStep.USAGE_DISCLOSURE -> Step(
                    title = R.string.usage_disclosure_title,
                    primary = R.string.usage_disclosure_accept to {
                        scope.launch { container.onboardingRepository.acceptUsageDisclosure() }
                        next(disclosureAccepted = true)
                    },
                    secondary = R.string.usage_disclosure_decline to { next(disclosureAccepted = false) },
                ) { UsageDisclosureContent() }

                OnboardingStep.USAGE_ACCESS -> PermissionStep(
                    title = R.string.onboarding_usage_title,
                    body = R.string.onboarding_usage_body,
                    granted = granted.usageAccess,
                    onGrant = { PermissionIntents.usageAccess(context) },
                    onNext = { next() },
                )

                OnboardingStep.OVERLAY -> PermissionStep(
                    title = R.string.onboarding_overlay_title,
                    body = R.string.onboarding_overlay_body,
                    granted = granted.overlay,
                    onGrant = { PermissionIntents.overlay(context) },
                    onNext = { next() },
                )

                OnboardingStep.NOTIFICATIONS -> PermissionStep(
                    title = R.string.onboarding_notifications_title,
                    body = R.string.onboarding_notifications_body,
                    granted = granted.notifications,
                    grantLabel = R.string.onboarding_allow,
                    onGrant = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onNext = { next() },
                )

                OnboardingStep.BATTERY_TIP -> Step(
                    title = R.string.onboarding_battery_title,
                    primary = R.string.onboarding_finish to { next() },
                ) {
                    Text(stringResource(R.string.onboarding_battery_body), style = MaterialTheme.typography.bodyLarge)
                    if (granted.batteryUnrestricted) {
                        GrantedLabel(R.string.onboarding_battery_unrestricted)
                    } else {
                        OutlinedButton(onClick = { PermissionIntents.batterySettings(context) }) {
                            Text(stringResource(R.string.onboarding_open_battery_settings))
                        }
                    }
                    if (PermissionStatus.isXiaomiFamily) {
                        Text(stringResource(R.string.onboarding_battery_xiaomi), style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = { PermissionIntents.autostart(context) }) {
                            Text(stringResource(R.string.onboarding_open_autostart))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Step(
    title: Int,
    primary: Pair<Int, () -> Unit>,
    secondary: Pair<Int, () -> Unit>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
    Spacer(Modifier.height(16.dp))
    Button(onClick = primary.second, modifier = Modifier.fillMaxWidth()) { Text(stringResource(primary.first)) }
    secondary?.let { (label, onClick) ->
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) }
    }
}

@Composable
private fun ColumnScope.PermissionStep(
    title: Int,
    body: Int,
    granted: Boolean,
    onGrant: () -> Unit,
    onNext: () -> Unit,
    grantLabel: Int = R.string.onboarding_open_settings,
) {
    Step(
        title = title,
        primary = if (granted) R.string.onboarding_continue to onNext else grantLabel to onGrant,
        secondary = if (granted) null else R.string.onboarding_skip to onNext,
    ) {
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
        if (granted) GrantedLabel(R.string.onboarding_granted)
    }
}

@Composable
private fun GrantedLabel(text: Int) {
    Text("✓ " + stringResource(text), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Bullet(text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("•", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StepDots(current: Int, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == current) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}
