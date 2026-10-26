package com.noikonomist.slapme.debug

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.noikonomist.slapme.slap.HomeLauncher

/**
 * Was the home screen really reached? `startActivity` gives no signal when a background launch is
 * blocked, so this looks at StrictMode (Android 16+) and at the usage events after the request.
 */
object HomeLaunchCheck {

    // Both checks are deprecated at compileSdk 37; checking our own uid/package is still what they're for.
    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun verdict(context: Context, requestedAtMillis: Long): String {
        if (DebugState.blockedLaunchAtMillis >= requestedAtMillis - 1_000) return "BLOCKED (StrictMode)"
        if (!hasUsageAccess(context)) return "UNVERIFIED (no usage access; see logcat ActivityTaskManager)"
        val launcher = HomeLauncher.launcherPackage(context) ?: return "UNVERIFIED (no launcher resolved)"

        val events = context.getSystemService(UsageStatsManager::class.java)
            .queryEvents(requestedAtMillis - 200, System.currentTimeMillis())
        val event = UsageEvents.Event()
        val resumed = mutableListOf<String>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == RESUMED) resumed += event.packageName
        }
        return if (launcher in resumed) {
            "VERIFIED ($launcher resumed)"
        } else {
            "NOT REACHED (resumed after request: ${resumed.ifEmpty { listOf("nothing") }})"
        }
    }

    /** ACTIVITY_RESUMED on API 29+; same value as MOVE_TO_FOREGROUND before. */
    private val RESUMED = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        UsageEvents.Event.ACTIVITY_RESUMED
    } else {
        @Suppress("DEPRECATION")
        UsageEvents.Event.MOVE_TO_FOREGROUND
    }
}
