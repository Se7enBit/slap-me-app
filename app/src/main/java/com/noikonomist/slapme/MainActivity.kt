package com.noikonomist.slapme

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.noikonomist.slapme.debug.DebugHooks
import com.noikonomist.slapme.monitor.MonitorNotifications
import com.noikonomist.slapme.monitor.MonitorStarter
import com.noikonomist.slapme.ui.AppRoot
import com.noikonomist.slapme.ui.theme.SlapMeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Counts notification taps that asked for the override screen. */
    private var overrideRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SlapMeTheme {
                AppRoot(overrideRequest)
            }
        }
        if (savedInstanceState == null) handle(intent)
    }

    override fun onResume() {
        super.onResume()
        // The app is in front, so starting the foreground service is always allowed here.
        lifecycleScope.launch { MonitorStarter.sync(this@MainActivity) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(MonitorNotifications.EXTRA_OPEN_OVERRIDE, false) == true) overrideRequest++
        DebugHooks.onIntent(this, intent)
    }
}
