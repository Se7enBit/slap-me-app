package com.noikonomist.slapme

import android.app.Application
import androidx.datastore.preferences.preferencesDataStoreFile
import com.noikonomist.slapme.data.ActivationRulesRepository
import com.noikonomist.slapme.data.EffectRecorder
import com.noikonomist.slapme.data.FocusSessionRepository
import com.noikonomist.slapme.data.HistoryRepository
import com.noikonomist.slapme.data.ScheduleRepository
import com.noikonomist.slapme.data.db.SlapMeDatabase
import com.noikonomist.slapme.data.settings.DiagnosticsRepository
import com.noikonomist.slapme.data.settings.EngineStateRepository
import com.noikonomist.slapme.data.settings.OnboardingRepository
import com.noikonomist.slapme.data.settings.SettingsRepository
import com.noikonomist.slapme.data.settings.preferencesStore
import com.noikonomist.slapme.debug.DebugHooks
import com.noikonomist.slapme.monitor.MonitorBridge
import com.noikonomist.slapme.monitor.MonitorStatus
import com.noikonomist.slapme.monitor.MonitorStore
import com.noikonomist.slapme.monitor.PersistentMonitorStore
import com.noikonomist.slapme.slap.SlapHaptics
import com.noikonomist.slapme.ui.override.SwitchOff
import com.noikonomist.slapme.slap.SlapPipeline
import com.noikonomist.slapme.slap.SlapSound
import com.noikonomist.slapme.util.DeviceClock
import com.noikonomist.slapme.util.SlapLog
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

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
    /** Lives as long as the process: owns the DataStores, and runs work that must outlive a component. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val clock: Clock = DeviceClock

    val database: SlapMeDatabase by lazy { SlapMeDatabase.create(app) }

    /** One DataStore per file: settings and onboarding flags share this one. */
    private val settingsStore by lazy { preferencesStore(appScope) { app.preferencesDataStoreFile("settings") } }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(settingsStore) }
    val onboardingRepository: OnboardingRepository by lazy { OnboardingRepository(settingsStore) }
    val engineStateRepository: EngineStateRepository by lazy {
        EngineStateRepository(preferencesStore(appScope) { app.preferencesDataStoreFile("engine_state") })
    }
    val diagnosticsRepository: DiagnosticsRepository by lazy {
        DiagnosticsRepository(preferencesStore(appScope) { app.preferencesDataStoreFile("diagnostics") })
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

    val slapHaptics: SlapHaptics by lazy { SlapHaptics(app) }
    val slapPipeline: SlapPipeline by lazy { SlapPipeline(app, SlapSound(app), slapHaptics) }

    /** Live monitor status, published by the running MonitorService (empty when it isn't running). */
    val monitorStatus = MutableStateFlow(MonitorStatus())

    /** Snooze / override requests from the UI to the running monitor. */
    val monitorBridge = MonitorBridge()

    /** A switch-off parked behind the friction override during a lockout. */
    @Volatile
    var pendingSwitchOff: SwitchOff? = null
    val monitorStore: MonitorStore by lazy {
        PersistentMonitorStore(engineStateRepository, effectRecorder, diagnosticsRepository)
    }
}
