package com.noikonomist.slapme.debug

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.compose.runtime.Composable

/** Release stand-ins: the debug menu does not exist in release builds. */
object DebugHooks {
    fun onAppCreate(app: Application) = Unit

    fun onIntent(activity: Activity, intent: Intent?) = Unit
}

@Composable
fun DebugPanel() = Unit
