package com.noikonomist.slapme.monitor

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.noikonomist.slapme.SlapMeApp
import com.noikonomist.slapme.util.SlapLog
import kotlinx.coroutines.flow.first

/**
 * Starts the monitor when the app is armed. The service stops itself once it isn't, so callers only
 * need [sync] after anything that may arm it: app opened, boot, settings or session changes.
 */
object MonitorStarter {

    suspend fun sync(context: Context) {
        val container = (context.applicationContext as SlapMeApp).container
        val armed = container.activationRulesRepository.rules.first().isArmed(container.clock.instant())
        if (armed) start(context)
    }

    fun start(context: Context) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: started from the background without an exemption.
            SlapLog.w("Monitor start not allowed right now", e)
        }
    }
}
