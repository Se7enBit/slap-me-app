package com.noikonomist.slapme.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.noikonomist.slapme.util.UsageAccess

/** The app in front, from UsageStatsManager events read incrementally on each tick. */
class UsageSource(context: Context) {
    private val app = context.applicationContext
    private val usageStats = app.getSystemService(UsageStatsManager::class.java)
    private val tracker = ForegroundTracker()
    private var readUntil: Long? = null

    /** Null without usage access, or if no app came to the front in the look-back window. */
    fun foregroundPackage(nowMillis: Long): String? {
        if (!UsageAccess.isGranted(app)) return null
        // Overlap the previous window a little: events can be written slightly late.
        val from = readUntil?.let { it - OVERLAP_MS } ?: (nowMillis - LOOKBACK_MS)
        val events = usageStats.queryEvents(from, nowMillis) ?: return tracker.foreground
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == RESUMED) tracker.onResumed(event.packageName, event.timeStamp)
        }
        readUntil = nowMillis
        return tracker.foreground
    }

    companion object {
        private const val OVERLAP_MS = 3_000L

        /** First read after the service starts: find whatever is already in front. */
        private const val LOOKBACK_MS = 6 * 60 * 60 * 1_000L

        /** ACTIVITY_RESUMED on API 29+; same value as MOVE_TO_FOREGROUND before. */
        val RESUMED = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }
    }
}
