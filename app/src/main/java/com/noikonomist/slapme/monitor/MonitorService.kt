package com.noikonomist.slapme.monitor

import android.app.KeyguardManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.noikonomist.slapme.AppContainer
import com.noikonomist.slapme.SlapMeApp
import com.noikonomist.slapme.engine.EngineConfig
import com.noikonomist.slapme.engine.Foreground
import com.noikonomist.slapme.engine.Observation
import com.noikonomist.slapme.engine.OverrideRejection
import com.noikonomist.slapme.util.SlapLog
import com.noikonomist.slapme.util.UsageAccess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service (type specialUse) that runs while the app is armed. It turns Android signals into
 * engine observations, ticking only while the screen is interactive, and carries out what [MonitorCore]
 * decides. All work runs on the main thread, one step at a time.
 */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container: AppContainer get() = (application as SlapMeApp).container
    private val core = CompletableDeferred<MonitorCore>()

    /** Same as [core] once created, for synchronous callers. */
    private var monitor: MonitorCore? = null
    private var tickJob: Job? = null
    private var stopping = false

    private lateinit var notifications: MonitorNotifications
    private lateinit var usage: UsageSource
    private lateinit var exceptions: ExceptionResolver
    private lateinit var power: PowerManager
    private lateinit var keyguard: KeyguardManager
    private val screenReceiver = ScreenStateReceiver(::onScreenChanged)

    /** An AudioManager.OnModeChangedListener on Android 12+ (the type doesn't exist before). */
    private var audioModeListener: Any? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications = MonitorNotifications(this).apply { createChannels() }
        usage = UsageSource(this)
        exceptions = ExceptionResolver(this, container.clock)
        power = getSystemService(PowerManager::class.java)
        keyguard = getSystemService(KeyguardManager::class.java)
        ContextCompat.registerReceiver(this, screenReceiver, ScreenStateReceiver.filter(), ContextCompat.RECEIVER_NOT_EXPORTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listenToAudioMode()
        SlapLog.i("Monitor service created")
        scope.launch { run() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every startForegroundService() must be answered with startForeground(), even when already running.
        ServiceCompat.startForeground(
            this,
            MonitorNotifications.ID_ONGOING,
            notifications.ongoing(container.monitorStatus.value, UsageAccess.isGranted(this)),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        when (intent?.action) {
            ACTION_SNOOZE -> scope.launch {
                SlapLog.i("Snooze tapped")
                core.await().snooze()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        SlapLog.i("Monitor service destroyed")
        tickJob?.cancel()
        unregisterReceiver(screenReceiver)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) stopListeningToAudioMode()
        notifications.cancelWarning()
        monitor?.let { container.appScope.launch { it.flush() } }
        container.monitorStatus.value = MonitorStatus()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun run() {
        val monitor = MonitorCore(
            initialState = container.engineStateRepository.load(),
            clock = container.clock,
            store = container.monitorStore,
            actions = actions,
            statusFlow = container.monitorStatus,
        )
        this.monitor = monitor
        core.complete(monitor)

        scope.launch {
            container.monitorStatus
                .map { OngoingKey(it.active, it.level, it.thresholdMillis, it.lockoutUntilMillis, UsageAccess.isGranted(this@MonitorService)) }
                .distinctUntilChanged()
                .collect { notifications.showOngoing(container.monitorStatus.value, it.usageAccess) }
        }

        combine(container.settingsRepository.settings, container.activationRulesRepository.rules, ::Pair)
            .collect { (settings, rules) ->
                monitor.configure(settings, rules)
                if (monitor.isArmed()) ensureTicking() else stopMonitoring("not armed")
            }
    }

    private fun ensureTicking() {
        if (tickJob?.isActive == true || !power.isInteractive || stopping) return
        tickJob = scope.launch {
            val monitor = core.await()
            SlapLog.d("Ticking started")
            while (true) {
                monitor.observe(observation(monitor))
                if (!monitor.isArmed()) {
                    stopMonitoring("not armed")
                    break
                }
                if (!power.isInteractive) {
                    monitor.flush()
                    break
                }
                delay(monitor.tickDelay())
            }
            SlapLog.d("Ticking stopped")
        }
    }

    /** Calls start and end with the screen off (proximity sensor), when there are no ticks. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun listenToAudioMode() {
        val listener = AudioManager.OnModeChangedListener { mode ->
            SlapLog.d("Audio mode: $mode")
            scope.launch {
                val monitor = core.await()
                monitor.observe(observation(monitor))
            }
        }
        getSystemService(AudioManager::class.java).addOnModeChangedListener(mainExecutor, listener)
        audioModeListener = listener
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun stopListeningToAudioMode() {
        (audioModeListener as? AudioManager.OnModeChangedListener)?.let {
            getSystemService(AudioManager::class.java).removeOnModeChangedListener(it)
        }
    }

    private fun onScreenChanged(action: String) {
        SlapLog.d("Screen: $action")
        when (action) {
            Intent.ACTION_SCREEN_OFF -> scope.launch {
                val monitor = core.await()
                monitor.observe(observation(monitor))
                monitor.flush()
            }
            else -> if (tickJob?.isActive == true) {
                scope.launch {
                    val monitor = core.await()
                    monitor.observe(observation(monitor))
                }
            } else {
                ensureTicking()
            }
        }
    }

    private fun observation(monitor: MonitorCore): Observation {
        val interactive = power.isInteractive
        val foreground = if (interactive) {
            val navigation = monitor.settings?.navigationPackages ?: EngineConfig.Defaults.navigationPackages
            exceptions.classify(usage.foregroundPackage(container.clock.millis()), navigation)
        } else {
            Foreground.Unknown
        }
        return Observation(
            interactive = interactive,
            unlocked = !keyguard.isKeyguardLocked,
            inCall = exceptions.inCall(),
            foreground = foreground,
        )
    }

    private fun stopMonitoring(reason: String) {
        if (stopping) return
        stopping = true
        SlapLog.i("Stopping monitor: $reason")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private val actions = object : MonitorActions {
        override fun warn(slapAtMillis: Long, freeSnoozeAvailable: Boolean) {
            SlapLog.i("Warning: slap at $slapAtMillis (free snooze: $freeSnoozeAvailable)")
            val snooze = monitor?.settings?.engine?.snoozeDuration ?: EngineConfig.Defaults.snoozeDuration
            notifications.showWarning(slapAtMillis, freeSnoozeAvailable, snooze)
            container.slapHaptics.buzz()
        }

        override fun cancelWarning() = notifications.cancelWarning()

        override suspend fun slap(alwaysPlaySound: Boolean) {
            SlapLog.i("Slap!")
            container.slapPipeline.slap(alwaysPlaySound)
            notifications.showSlapped()
        }

        override fun overrideRejected(reason: OverrideRejection) {
            SlapLog.i("Override rejected: $reason")
        }
    }

    private data class OngoingKey(
        val active: Boolean,
        val level: Int,
        val thresholdMillis: Long,
        val lockoutUntilMillis: Long?,
        val usageAccess: Boolean,
    )

    companion object {
        const val ACTION_SNOOZE = "com.noikonomist.slapme.action.SNOOZE"
    }
}
