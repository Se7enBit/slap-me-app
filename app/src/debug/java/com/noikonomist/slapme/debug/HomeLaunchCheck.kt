package com.noikonomist.slapme.debug

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.noikonomist.slapme.monitor.UsageSource
import com.noikonomist.slapme.slap.HomeLauncher
import com.noikonomist.slapme.util.UsageAccess

/**
 * Was the home screen really reached? `startActivity` gives no signal when a background launch is
 * blocked, so this looks at StrictMode (Android 16+) and at the usage events after the request.
 */
object HomeLaunchCheck {

    fun verdict(context: Context, requestedAtMillis: Long): String {
        if (DebugState.blockedLaunchAtMillis >= requestedAtMillis - 1_000) return "BLOCKED (StrictMode)"
        if (!UsageAccess.isGranted(context)) return "UNVERIFIED (no usage access; see logcat ActivityTaskManager)"
        val launcher = HomeLauncher.launcherPackage(context) ?: return "UNVERIFIED (no launcher resolved)"

        val events = context.getSystemService(UsageStatsManager::class.java)
            .queryEvents(requestedAtMillis - 200, System.currentTimeMillis())
        val event = UsageEvents.Event()
        val resumed = mutableListOf<String>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageSource.RESUMED) resumed += event.packageName
        }
        return if (launcher in resumed) {
            "VERIFIED ($launcher resumed)"
        } else {
            "NOT REACHED (resumed after request: ${resumed.ifEmpty { listOf("nothing") }})"
        }
    }
}
