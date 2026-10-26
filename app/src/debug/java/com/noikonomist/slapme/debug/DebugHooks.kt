package com.noikonomist.slapme.debug

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.StrictMode
import com.noikonomist.slapme.slap.SlapReport
import com.noikonomist.slapme.util.SlapLog
import kotlinx.coroutines.flow.MutableStateFlow

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
        val delay = intent?.getIntExtra(EXTRA_SLAP_DELAY, -1) ?: -1
        if (delay < 0) return
        val alwaysPlay = intent?.getBooleanExtra(EXTRA_ALWAYS_PLAY, DebugState.alwaysPlaySound.value) ?: false
        SlapLog.i("Debug slap requested by intent: delay=${delay}s alwaysPlay=$alwaysPlay")
        DebugSlapService.start(activity, delay, alwaysPlay)
    }
}
