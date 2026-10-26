package com.noikonomist.slapme.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.noikonomist.slapme.SlapMeApp
import com.noikonomist.slapme.util.SlapLog
import kotlinx.coroutines.launch

/** Screen on/off and unlock. These can only be received by a receiver registered at runtime. */
class ScreenStateReceiver(private val onChange: (action: String) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.action?.let(onChange)
    }

    companion object {
        fun filter() = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
    }
}

/** Re-arms the monitor after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        SlapLog.i("Received ${intent.action}; syncing the monitor")
        val pending = goAsync()
        (context.applicationContext as SlapMeApp).container.appScope.launch {
            try {
                MonitorStarter.sync(context)
            } finally {
                pending.finish()
            }
        }
    }
}
