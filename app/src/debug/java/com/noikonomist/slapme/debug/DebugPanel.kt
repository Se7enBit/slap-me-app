package com.noikonomist.slapme.debug

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.noikonomist.slapme.R
import com.noikonomist.slapme.SlapMeApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DebugPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var overlayGranted by remember { mutableStateOf(false) }
    var notificationsGranted by remember { mutableStateOf(false) }
    var usageGranted by remember { mutableStateOf(false) }
    val alwaysPlay by DebugState.alwaysPlaySound.collectAsState()
    val lastResult by DebugState.lastResult.collectAsState()

    LifecycleResumeEffect(Unit) {
        overlayGranted = Settings.canDrawOverlays(context)
        notificationsGranted = notificationsGranted(context)
        usageGranted = HomeLaunchCheck.hasUsageAccess(context)
        onPauseOrDispose { }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()
        Text(stringResource(R.string.debug_title), style = MaterialTheme.typography.titleMedium)

        PermissionRow(stringResource(R.string.debug_permission_overlay), overlayGranted) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()),
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(stringResource(R.string.debug_permission_notifications), notificationsGranted) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        PermissionRow(stringResource(R.string.debug_permission_usage), usageGranted) {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.debug_always_play), Modifier.weight(1f))
            Switch(checked = alwaysPlay, onCheckedChange = { DebugState.alwaysPlaySound.value = it })
        }

        Button(
            onClick = {
                scope.launch {
                    val report = (context.applicationContext as SlapMeApp).container.slapPipeline.slap(alwaysPlay)
                    delay(DebugSlapService.HOME_CHECK_DELAY_MS)
                    DebugState.record(report, HomeLaunchCheck.verdict(context, report.homeRequestedAtMillis))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.debug_slap_now)) }

        Button(
            onClick = { DebugSlapService.start(context, delaySeconds = 10, alwaysPlay = alwaysPlay) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.debug_slap_in_10)) }

        lastResult?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        if (granted) {
            Text(stringResource(R.string.debug_granted), color = MaterialTheme.colorScheme.primary)
        } else {
            OutlinedButton(onClick = onGrant) { Text(stringResource(R.string.debug_grant)) }
        }
    }
}

private fun notificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
