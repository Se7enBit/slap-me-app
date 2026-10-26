package com.noikonomist.slapme.permissions

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.noikonomist.slapme.util.SlapLog
import com.noikonomist.slapme.util.UsageAccess

object PermissionStatus {
    fun read(context: Context) = PermissionState(
        usageAccess = UsageAccess.isGranted(context),
        overlay = Settings.canDrawOverlays(context),
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        batteryUnrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
    )

    val isXiaomiFamily: Boolean get() = PermissionChecklist.isXiaomiFamily(Build.MANUFACTURER, Build.BRAND)
}

/** System settings screens, each with fallbacks for OEMs that lack the precise one. */
object PermissionIntents {

    fun usageAccess(context: Context) = open(
        context,
        // Straight to Slap Me's page where supported (Android 10+), otherwise the list.
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri(context)).takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q },
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        appDetails(context),
    )

    fun overlay(context: Context) = open(
        context,
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context)),
        appDetails(context),
    )

    fun notificationSettings(context: Context) = open(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        appDetails(context),
    )

    /** The battery-optimization list. We only link to it; the app never requests the exemption itself. */
    fun batterySettings(context: Context) = open(
        context,
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        appDetails(context),
    )

    /** MIUI's Autostart list (needed for boot re-arming on Xiaomi), else the app's settings page. */
    fun autostart(context: Context) = open(
        context,
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        appDetails(context),
    )

    private fun appDetails(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    private fun packageUri(context: Context) = "package:${context.packageName}".toUri()

    private fun open(context: Context, vararg intents: Intent?): Boolean {
        for (intent in intents.filterNotNull()) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: ActivityNotFoundException) {
                SlapLog.d("No activity for ${intent.action ?: intent.component}; trying the next one")
            } catch (e: SecurityException) {
                SlapLog.d("Not allowed to open ${intent.action ?: intent.component}; trying the next one")
            }
        }
        SlapLog.w("Could not open any settings screen")
        return false
    }
}
