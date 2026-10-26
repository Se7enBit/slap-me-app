package com.noikonomist.slapme.util

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

object UsageAccess {
    // Both checks are deprecated at compileSdk 37; checking our own uid/package is still what they're for.
    @Suppress("DEPRECATION")
    fun isGranted(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
