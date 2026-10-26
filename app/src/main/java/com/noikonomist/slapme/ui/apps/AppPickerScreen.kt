package com.noikonomist.slapme.ui.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.noikonomist.slapme.R
import com.noikonomist.slapme.ui.appContainer
import com.noikonomist.slapme.ui.components.ScreenScaffold
import com.noikonomist.slapme.ui.settings.AppListKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Apps with a launcher icon (visible through the manifest's <queries>; no QUERY_ALL_PACKAGES). */
object InstalledApps {
    fun launcherApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return infos.map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
    }
}

@Composable
fun AppPickerScreen(kind: AppListKind, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = appContainer().settingsRepository
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsState(initial = null)
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { InstalledApps.launcherApps(context) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    // Sort by the selection as it was when the screen opened, so rows don't jump while ticking boxes.
    var initialSelection by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(settings) {
        if (initialSelection == null) settings?.let { initialSelection = kind.selected(it) }
    }

    val title = stringResource(
        when (kind) {
            AppListKind.DISTRACTING -> R.string.apps_title_distracting
            AppListKind.ALLOWED -> R.string.apps_title_allowed
            AppListKind.NAVIGATION -> R.string.apps_title_navigation
        },
    )
    ScreenScaffold(title = title, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                stringResource(
                    when (kind) {
                        AppListKind.DISTRACTING -> R.string.apps_hint_distracting
                        AppListKind.ALLOWED -> R.string.apps_hint_allowed
                        AppListKind.NAVIGATION -> R.string.apps_hint_navigation
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.apps_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            val all = apps
            val current = settings
            if (all == null || current == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            val selected = kind.selected(current)
            val shown = AppListFilter.apply(all, initialSelection ?: selected, query, context.packageName)
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    fun toggle() {
                        scope.launch {
                            repository.update { s ->
                                val now = kind.selected(s)
                                kind.withSelected(s, if (app.packageName in now) now - app.packageName else now + app.packageName)
                            }
                        }
                    }
                    ListItem(
                        leadingContent = { AppIcon(app.packageName) },
                        headlineContent = { Text(app.label) },
                        trailingContent = { Checkbox(checked = checked, onCheckedChange = { toggle() }) },
                        modifier = Modifier.clickable { toggle() },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull()
        }
    }
    val bitmap = icon
    if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp)) else Box(Modifier.size(40.dp))
}
