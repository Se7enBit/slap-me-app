package com.noikonomist.slapme.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.noikonomist.slapme.R
import com.noikonomist.slapme.data.settings.OnboardingState
import com.noikonomist.slapme.permissions.Fix
import com.noikonomist.slapme.permissions.FixItem
import com.noikonomist.slapme.permissions.PermissionChecklist
import com.noikonomist.slapme.permissions.PermissionIntents
import com.noikonomist.slapme.permissions.PermissionStatus
import com.noikonomist.slapme.permissions.Severity
import com.noikonomist.slapme.ui.appContainer
import com.noikonomist.slapme.ui.onboarding.UsageDisclosureDialog
import com.noikonomist.slapme.ui.rememberPermissions
import kotlinx.coroutines.launch

/** One card per missing permission (or the battery tip), each saying why it matters and how to fix it. */
@Composable
fun FixItCards(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val permissions = rememberPermissions()
    val onboarding by container.onboardingRepository.state.collectAsState(initial = OnboardingState())
    val lastGap by container.diagnosticsRepository.lastServiceGap.collectAsState(initial = null)
    var showDisclosure by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissions.refresh()
        // Denied for good: the system no longer shows the dialog, so send the user to the settings.
        val permanentlyDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        if (!granted && permanentlyDenied) PermissionIntents.notificationSettings(context)
    }

    val now = System.currentTimeMillis()
    val fixes = PermissionChecklist.fixes(permissions.state, onboarding.batteryTipDismissedAtMillis, lastGap?.atMillis, now)
    val recentlyKilled = lastGap?.let { now - it.atMillis <= 7 * 24 * 60 * 60 * 1_000L } == true

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (fix in fixes) {
            when (fix.item) {
                FixItem.USAGE_ACCESS -> FixCard(fix, R.string.fix_usage_title, R.string.fix_usage_text) {
                    Button(onClick = {
                        if (onboarding.usageDisclosureAccepted) PermissionIntents.usageAccess(context) else showDisclosure = true
                    }) { Text(stringResource(R.string.fix_turn_on)) }
                }

                FixItem.OVERLAY -> FixCard(fix, R.string.fix_overlay_title, R.string.fix_overlay_text) {
                    Button(onClick = { PermissionIntents.overlay(context) }) { Text(stringResource(R.string.fix_turn_on)) }
                }

                FixItem.NOTIFICATIONS -> FixCard(fix, R.string.fix_notifications_title, R.string.fix_notifications_text) {
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            PermissionIntents.notificationSettings(context)
                        }
                    }) { Text(stringResource(R.string.fix_turn_on)) }
                }

                FixItem.BATTERY -> FixCard(
                    fix,
                    R.string.fix_battery_title,
                    if (recentlyKilled) R.string.fix_battery_text_killed else R.string.fix_battery_text,
                    extra = if (PermissionStatus.isXiaomiFamily) R.string.fix_battery_xiaomi else null,
                ) {
                    Button(onClick = { PermissionIntents.batterySettings(context) }) {
                        Text(stringResource(R.string.fix_battery_settings))
                    }
                    if (PermissionStatus.isXiaomiFamily) {
                        OutlinedButton(onClick = { PermissionIntents.autostart(context) }) {
                            Text(stringResource(R.string.onboarding_open_autostart))
                        }
                    }
                    TextButton(onClick = {
                        scope.launch { container.onboardingRepository.dismissBatteryTip(System.currentTimeMillis()) }
                    }) { Text(stringResource(R.string.fix_dismiss)) }
                }
            }
        }
    }

    if (showDisclosure) {
        UsageDisclosureDialog(
            onAccept = {
                showDisclosure = false
                scope.launch { container.onboardingRepository.acceptUsageDisclosure() }
                PermissionIntents.usageAccess(context)
            },
            onDismiss = { showDisclosure = false },
        )
    }
}

@Composable
private fun FixCard(
    fix: Fix,
    title: Int,
    text: Int,
    extra: Int? = null,
    actions: @Composable () -> Unit,
) {
    val colors = when (fix.severity) {
        Severity.REQUIRED -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        Severity.RECOMMENDED -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        Severity.TIP -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            extra?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        }
    }
}
