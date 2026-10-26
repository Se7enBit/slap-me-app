package com.noikonomist.slapme.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noikonomist.slapme.R

/**
 * The prominent disclosure for Usage Access: what is read, why, and that it never leaves the phone.
 * Shown, and accepted, before the app ever opens the usage-access settings.
 */
@Composable
fun UsageDisclosureContent(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.usage_disclosure_intro), style = MaterialTheme.typography.bodyLarge)
        DisclosureSection(R.string.usage_disclosure_reads_title, R.string.usage_disclosure_reads)
        DisclosureSection(R.string.usage_disclosure_why_title, R.string.usage_disclosure_why)
        DisclosureSection(R.string.usage_disclosure_privacy_title, R.string.usage_disclosure_privacy)
    }
}

@Composable
private fun DisclosureSection(title: Int, body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun UsageDisclosureDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.usage_disclosure_title)) },
        text = { UsageDisclosureContent(Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.usage_disclosure_accept)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.usage_disclosure_decline)) } },
    )
}
