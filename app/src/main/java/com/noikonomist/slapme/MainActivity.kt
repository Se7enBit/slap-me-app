package com.noikonomist.slapme

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.noikonomist.slapme.debug.DebugHooks
import com.noikonomist.slapme.ui.home.HomeScreen
import com.noikonomist.slapme.ui.theme.SlapMeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SlapMeTheme {
                HomeScreen()
            }
        }
        if (savedInstanceState == null) DebugHooks.onIntent(this, intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DebugHooks.onIntent(this, intent)
    }
}
