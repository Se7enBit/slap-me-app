package com.noikonomist.slapme

import android.app.Application
import androidx.datastore.preferences.preferencesDataStoreFile
import com.noikonomist.slapme.data.ActivationRulesRepository
import com.noikonomist.slapme.data.EffectRecorder
import com.noikonomist.slapme.data.FocusSessionRepository
import com.noikonomist.slapme.data.HistoryRepository
import com.noikonomist.slapme.data.ScheduleRepository
import com.noikonomist.slapme.data.db.SlapMeDatabase
import com.noikonomist.slapme.data.settings.EngineStateRepository
import com.noikonomist.slapme.data.settings.SettingsRepository
import com.noikonomist.slapme.data.settings.preferencesStore
import com.noikonomist.slapme.debug.DebugHooks
import com.noikonomist.slapme.slap.SlapHaptics
import com.noikonomist.slapme.slap.SlapPipeline
import com.noikonomist.slapme.slap.SlapSound
import com.noikonomist.slapme.util.DeviceClock
import com.noikonomist.slapme.util.SlapLog
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

/** Manual DI root. Everything is created on first use. */
class AppContainer(app: Application) {
    /** Lives as long as the process; owns the DataStores. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val clock: Clock = DeviceClock

    val database: SlapMeDatabase by lazy { SlapMeDatabase.create(app) }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(preferencesStore(appScope) { app.preferencesDataStoreFile("settings") })
    }
    val engineStateRepository: EngineStateRepository by lazy {
        EngineStateRepository(preferencesStore(appScope) { app.preferencesDataStoreFile("engine_state") })
    }

    val scheduleRepository: ScheduleRepository by lazy { ScheduleRepository(database.scheduleBlockDao()) }
    val focusSessionRepository: FocusSessionRepository by lazy {
        FocusSessionRepository(database.focusSessionDao(), clock)
    }
    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(database.slapEventDao(), database.overrideEventDao(), database.dailyUsageDao())
    }
    val effectRecorder: EffectRecorder by lazy {
        EffectRecorder(database.slapEventDao(), database.overrideEventDao(), database.dailyUsageDao(), database.focusSessionDao())
    }
    val activationRulesRepository: ActivationRulesRepository by lazy {
        ActivationRulesRepository(scheduleRepository, focusSessionRepository, settingsRepository)
    }

    val slapPipeline: SlapPipeline by lazy { SlapPipeline(app, SlapSound(app), SlapHaptics(app)) }
}
