package com.noikonomist.slapme.debug

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.StrictMode
import com.noikonomist.slapme.SlapMeApp
import com.noikonomist.slapme.monitor.MonitorStarter
import com.noikonomist.slapme.slap.SlapReport
import com.noikonomist.slapme.util.SlapLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Debug-only state shared by the panel and the spike service. */
object DebugState {
    val alwaysPlaySound = MutableStateFlow(false)
    val lastResult = MutableStateFlow<String?>(null)

    /** Wall-clock time StrictMode last reported a blocked background activity launch (Android 16+). */
    @Volatile
    var blockedLaunchAtMillis = 0L

    fun record(report: SlapReport, homeVerdict: String): String =
        "overlay shown: ${report.overlayShown}\nhome: $homeVerdict\nsound: ${report.sound}\nhaptics: ${report.haptics}"
            .also { lastResult.value = it }
}

object DebugHooks {
    /** `adb shell am start -n com.noikonomist.slapme/.MainActivity --ei debug_slap_delay 10` */
    const val EXTRA_SLAP_DELAY = "debug_slap_delay"
    const val EXTRA_ALWAYS_PLAY = "debug_always_play"

    /** `--es debug_preset fast|default` and `--ez debug_always_on true|false`, to drive tests over adb. */
    const val EXTRA_PRESET = "debug_preset"
    const val EXTRA_ALWAYS_ON = "debug_always_on"

    fun onAppCreate(app: Application) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectBlockedBackgroundActivityLaunch()
                    .penaltyLog()
                    .penaltyListener(app.mainExecutor) { violation ->
                        DebugState.blockedLaunchAtMillis = System.currentTimeMillis()
                        SlapLog.e("StrictMode: background activity launch BLOCKED", violation)
                    }
                    .build(),
            )
        }
    }

    fun onIntent(activity: Activity, intent: Intent?) {
        if (intent != null && (intent.hasExtra(EXTRA_PRESET) || intent.hasExtra(EXTRA_ALWAYS_ON))) applySettings(activity, intent)
        val delay = intent?.getIntExtra(EXTRA_SLAP_DELAY, -1) ?: -1
        if (delay < 0) return
        val alwaysPlay = intent?.getBooleanExtra(EXTRA_ALWAYS_PLAY, DebugState.alwaysPlaySound.value) ?: false
        SlapLog.i("Debug slap requested by intent: delay=${delay}s alwaysPlay=$alwaysPlay")
        DebugSlapService.start(activity, delay, alwaysPlay)
    }
}

private fun applySettings(activity: Activity, intent: Intent) {
    val container = (activity.application as SlapMeApp).container
    val preset = intent.getStringExtra(DebugHooks.EXTRA_PRESET)
    val alwaysOn = if (intent.hasExtra(DebugHooks.EXTRA_ALWAYS_ON)) intent.getBooleanExtra(DebugHooks.EXTRA_ALWAYS_ON, false) else null
    SlapLog.i("Debug settings by intent: preset=$preset alwaysOn=$alwaysOn")
    container.appScope.launch {
        container.settingsRepository.update { current ->
            val timed = when (preset) {
                "fast" -> DebugPresets.fast(current)
                "default" -> DebugPresets.defaults(current)
                else -> current
            }
            if (alwaysOn != null) timed.copy(alwaysOn = alwaysOn) else timed
        }
        MonitorStarter.sync(activity.applicationContext)
    }
}
