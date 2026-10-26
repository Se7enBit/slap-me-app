package com.noikonomist.slapme

import android.app.Application
import com.noikonomist.slapme.util.SlapLog

class SlapMeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SlapLog.i("App started")
    }
}

/** Manual DI root. Repositories, engine and services get wired here as they are added. */
class AppContainer(@Suppress("unused") private val app: Application)
