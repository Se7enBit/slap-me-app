package com.noikonomist.slapme.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.noikonomist.slapme.AppContainer
import com.noikonomist.slapme.SlapMeApp
import com.noikonomist.slapme.permissions.PermissionState
import com.noikonomist.slapme.permissions.PermissionStatus
import com.noikonomist.slapme.ui.home.HomeScreen
import com.noikonomist.slapme.ui.onboarding.OnboardingScreen
import kotlinx.coroutines.launch

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as SlapMeApp).container

/** Onboarding until it's finished, then Home. Blank while the flag loads, so neither screen flashes. */
@Composable
fun AppRoot() {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val onboarding by container.onboardingRepository.state.collectAsState(initial = null)

    when (val state = onboarding) {
        null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        else -> if (state.completed) {
            HomeScreen()
        } else {
            OnboardingScreen(onFinished = { scope.launch { container.onboardingRepository.complete() } })
        }
    }
}

/** Permission state that refreshes whenever the screen resumes (e.g. back from system settings). */
@Stable
class PermissionsHolder(private val context: Context) {
    var state: PermissionState by mutableStateOf(PermissionStatus.read(context))
        private set

    fun refresh() {
        state = PermissionStatus.read(context)
    }
}

@Composable
fun rememberPermissions(): PermissionsHolder {
    val context = LocalContext.current
    val holder = remember { PermissionsHolder(context.applicationContext) }
    LifecycleResumeEffect(Unit) {
        holder.refresh()
        onPauseOrDispose { }
    }
    return holder
}
