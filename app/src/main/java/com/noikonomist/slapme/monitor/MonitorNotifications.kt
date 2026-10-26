package com.noikonomist.slapme.monitor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.noikonomist.slapme.MainActivity
import com.noikonomist.slapme.R
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** The monitor's notifications: the ongoing one (required for the foreground service), warnings and "Slapped!". */
class MonitorNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.monitor_channel_ongoing), NotificationManager.IMPORTANCE_LOW),
        )
        system.createNotificationChannel(
            // High importance for the heads-up; silent, the monitor buzzes itself.
            NotificationChannel(CHANNEL_WARNING, context.getString(R.string.monitor_channel_warning), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    setSound(null, null)
                    enableVibration(false)
                },
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_SLAPPED, context.getString(R.string.monitor_channel_slapped), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun ongoing(status: MonitorStatus, usageAccess: Boolean): Notification {
        val (title, text) = when {
            !usageAccess -> R.string.monitor_ongoing_title_armed to context.getString(R.string.monitor_ongoing_text_no_usage_access)
            status.lockoutUntilMillis != null ->
                R.string.monitor_ongoing_title_lockout to context.getString(R.string.monitor_ongoing_text_lockout, time(status.lockoutUntilMillis))
            status.active -> R.string.monitor_ongoing_title_active to context.getString(
                R.string.monitor_ongoing_text_active,
                status.level + 1,
                duration(status.thresholdMillis.milliseconds),
            )
            else -> R.string.monitor_ongoing_title_armed to context.getString(R.string.monitor_ongoing_text_waiting)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_slap)
            .setContentTitle(context.getString(title))
            .setContentText(text)
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (status.lockoutUntilMillis != null) {
            builder.addAction(0, context.getString(R.string.override_need_more_time), openApp(openOverride = true))
        }
        return builder.build()
    }

    fun showOngoing(status: MonitorStatus, usageAccess: Boolean) = post(ID_ONGOING, ongoing(status, usageAccess))

    fun showWarning(slapAtMillis: Long, freeSnoozeAvailable: Boolean, snooze: Duration) {
        val builder = NotificationCompat.Builder(context, CHANNEL_WARNING)
            .setSmallIcon(R.drawable.ic_stat_slap)
            .setContentTitle(context.getString(R.string.monitor_warning_title))
            .setContentText(context.getString(R.string.monitor_warning_text))
            .setWhen(slapAtMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter((slapAtMillis - System.currentTimeMillis()).coerceAtLeast(0) + 5_000)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(openOverride = true))
            .setAutoCancel(true)
        if (freeSnoozeAvailable) {
            builder.addAction(0, context.getString(R.string.monitor_warning_snooze, duration(snooze)), snoozeIntent())
        } else {
            builder.addAction(0, context.getString(R.string.override_need_more_time), openApp(openOverride = true))
        }
        // A new warning makes an older "Slapped!" note stale.
        manager.cancel(ID_SLAPPED)
        post(ID_WARNING, builder.build())
    }

    fun cancelWarning() = manager.cancel(ID_WARNING)

    fun showSlapped() = post(
        ID_SLAPPED,
        NotificationCompat.Builder(context, CHANNEL_SLAPPED)
            .setSmallIcon(R.drawable.ic_stat_slap)
            .setContentTitle(context.getString(R.string.monitor_slapped_title))
            .setContentText(context.getString(R.string.monitor_slapped_text))
            .setContentIntent(openApp(openOverride = true))
            .setAutoCancel(true)
            .setTimeoutAfter(SLAPPED_TIMEOUT_MS)
            .build(),
    )

    /** Without the notification permission (API 33+) posting is skipped; the foreground service still runs. */
    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) manager.notify(id, notification)
    }

    private fun openApp(openOverride: Boolean = false): PendingIntent = PendingIntent.getActivity(
        context,
        if (openOverride) REQUEST_OPEN_OVERRIDE else REQUEST_OPEN_APP,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_OVERRIDE, openOverride),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun snoozeIntent(): PendingIntent = PendingIntent.getService(
        context,
        REQUEST_SNOOZE,
        Intent(context, MonitorService::class.java).setAction(MonitorService.ACTION_SNOOZE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun time(millis: Long): String = DateFormat.getTimeFormat(context).format(Date(millis))

    private fun duration(d: Duration): String =
        if (d.inWholeSeconds < 60 || d.inWholeSeconds % 60 != 0L) {
            context.getString(R.string.duration_seconds, d.inWholeSeconds)
        } else {
            context.getString(R.string.duration_minutes, d.inWholeMinutes)
        }

    companion object {
        const val ID_ONGOING = 1
        const val ID_WARNING = 2
        const val ID_SLAPPED = 3

        /** Opens the override ("Need more time?") screen. */
        const val EXTRA_OPEN_OVERRIDE = "open_override"

        private const val CHANNEL_ONGOING = "monitor"
        private const val CHANNEL_WARNING = "warning"
        private const val CHANNEL_SLAPPED = "slapped"
        private const val SLAPPED_TIMEOUT_MS = 10 * 60 * 1_000L
        private const val REQUEST_OPEN_APP = 1
        private const val REQUEST_OPEN_OVERRIDE = 2
        private const val REQUEST_SNOOZE = 3
    }
}
