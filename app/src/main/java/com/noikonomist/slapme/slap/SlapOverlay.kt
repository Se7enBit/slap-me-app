package com.noikonomist.slapme.slap

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.noikonomist.slapme.util.SlapLog

/**
 * A full-screen, touch-blocking overlay window hosting Compose. A window added through WindowManager
 * has no Activity, so it gets its own lifecycle and saved-state owners. Main thread only.
 */
class SlapOverlay(context: Context) {
    private val context = context.applicationContext
    private val windowManager = this.context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    /** Returns false if the overlay permission is missing or the window could not be added. */
    fun show(content: @Composable () -> Unit): Boolean {
        if (view != null) return true
        if (!canShow()) return false

        val owner = OverlayOwner().apply { start() }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent(content)
        }
        return try {
            windowManager.addView(composeView, layoutParams())
            this.owner = owner
            this.view = composeView
            true
        } catch (e: RuntimeException) {
            SlapLog.e("Adding the slap overlay failed", e)
            owner.destroy()
            false
        }
    }

    fun remove() {
        view?.let { v ->
            try {
                windowManager.removeViewImmediate(v)
            } catch (e: RuntimeException) {
                SlapLog.e("Removing the slap overlay failed", e)
            }
        }
        owner?.destroy()
        view = null
        owner = null
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Not focusable (keys go to the app below), but touchable: it swallows taps during the slap.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "SlapOverlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setFitInsetsTypes(0)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private class OverlayOwner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun start() {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            if (registry.currentState != Lifecycle.State.INITIALIZED) {
                registry.currentState = Lifecycle.State.DESTROYED
            }
        }
    }
}
