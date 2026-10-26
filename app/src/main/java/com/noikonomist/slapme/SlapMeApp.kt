package com.noikonomist.slapme

import android.app.Application
import com.noikonomist.slapme.debug.DebugHooks
import com.noikonomist.slapme.slap.SlapHaptics
import com.noikonomist.slapme.slap.SlapPipeline
import com.noikonomist.slapme.slap.SlapSound
import com.noikonomist.slapme.util.SlapLog

class SlapMeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        DebugHooks.onAppCreate(this)
        container = AppContainer(this)
        SlapLog.i("App started")
    }
}

/** Manual DI root. Repositories, engine and services get wired here as they are added. */
class AppContainer(app: Application) {
    val slapPipeline: SlapPipeline by lazy { SlapPipeline(app, SlapSound(app), SlapHaptics(app)) }
}
