package com.noikonomist.slapme.slap

import android.content.Context
import android.content.Intent
import com.noikonomist.slapme.util.SlapLog

object HomeLauncher {

    /**
     * Asks the system to show the home screen. Returns whether the request was accepted by
     * `startActivity`; a background-launch block is silent, so this does not prove Home was reached.
     */
    fun launch(context: Context): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: RuntimeException) {
        SlapLog.e("Home launch threw", e)
        false
    }

    fun launcherPackage(context: Context): String? =
        context.packageManager
            .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo
            ?.packageName
}
