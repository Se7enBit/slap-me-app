package com.noikonomist.slapme.monitor

import com.noikonomist.slapme.data.settings.Settings
import com.noikonomist.slapme.engine.ActivationRules
import com.noikonomist.slapme.engine.DailyStats
import com.noikonomist.slapme.engine.EngineConfig
import com.noikonomist.slapme.engine.EngineEffect
import com.noikonomist.slapme.engine.EngineEvent
import com.noikonomist.slapme.engine.EngineState
import com.noikonomist.slapme.engine.Observation
import com.noikonomist.slapme.engine.OverrideRejection
import com.noikonomist.slapme.engine.UsageEngine
import com.noikonomist.slapme.engine.UsageState
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persistence the monitor needs. */
interface MonitorStore {
    suspend fun saveState(state: EngineState)
    suspend fun record(effects: List<EngineEffect>, atMillis: Long)
    suspend fun recordToday(stats: DailyStats)
    suspend fun serviceGap(atMillis: Long, duration: Duration)
}

/** What the monitor does on screen. Implemented by the service. */
interface MonitorActions {
    fun warn(slapAtMillis: Long, freeSnoozeAvailable: Boolean)
    fun cancelWarning()
    suspend fun slap(alwaysPlaySound: Boolean)
    fun overrideRejected(reason: OverrideRejection)
}

/** Outcome of a snooze or friction-override request. */
sealed interface OverrideResult {
    data class Granted(val untilMillis: Long) : OverrideResult
    data class Rejected(val reason: OverrideRejection) : OverrideResult

    /** No monitor is running, so nothing is watching. */
    data object NotRunning : OverrideResult
}

/** Lets the UI reach the running monitor. The service attaches its core while it runs. */
class MonitorBridge {
    @Volatile
    private var core: MonitorCore? = null

    fun attach(core: MonitorCore) {
        this.core = core
    }

    fun detach(core: MonitorCore) {
        if (this.core === core) this.core = null
    }

    suspend fun snooze(): OverrideResult = core?.snooze() ?: OverrideResult.NotRunning

    suspend fun grantFrictionOverride(): OverrideResult = core?.grantFrictionOverride() ?: OverrideResult.NotRunning
}

/** A snapshot for the UI and the ongoing notification. */
data class MonitorStatus(
    val running: Boolean = false,
    val armed: Boolean = false,
    val active: Boolean = false,
    val usage: UsageState = UsageState.IDLE,
    val foregroundPackage: String? = null,
    val streakMillis: Long = 0,
    val thresholdMillis: Long = 0,
    val level: Int = 0,
    val lockoutUntilMillis: Long? = null,
    val overrideUntilMillis: Long? = null,
    /** The free snooze of the current period hasn't been used. */
    val freeSnoozeAvailable: Boolean = false,
)

/**
 * The monitor loop without Android: feeds observations and requests to the engine, carries out the
 * effects through [MonitorActions], and saves state and history through [MonitorStore]. All calls are
 * serialized, so a slap in progress and a snooze tap never interleave.
 */
class MonitorCore(
    initialState: EngineState,
    private val clock: Clock,
    private val store: MonitorStore,
    private val actions: MonitorActions,
    private val statusFlow: MutableStateFlow<MonitorStatus> = MutableStateFlow(MonitorStatus()),
) {
    private val mutex = Mutex()
    private var engine: UsageEngine? = null
    private var lastSavedAt: Long? = null
    private var lastTodayAt: Long? = null

    var state: EngineState = initialState
        private set
    var settings: Settings? = null
        private set
    private var rules: ActivationRules? = null

    val status: StateFlow<MonitorStatus> get() = statusFlow

    /** New settings or rules: rebuild the engine, keep the state. */
    suspend fun configure(settings: Settings, rules: ActivationRules) = mutex.withLock {
        this.settings = settings
        this.rules = rules
        engine = UsageEngine(settings.engine, rules, clock)
        publishStatus()
    }

    suspend fun observe(observation: Observation) {
        step(EngineEvent.Observe(observation))
    }

    suspend fun snooze(): OverrideResult = overrideResult(step(EngineEvent.RequestSnooze))

    suspend fun grantFrictionOverride(): OverrideResult = overrideResult(step(EngineEvent.GrantFrictionOverride))

    private fun overrideResult(effects: List<EngineEffect>?): OverrideResult {
        if (effects == null) return OverrideResult.NotRunning
        effects.filterIsInstance<EngineEffect.OverrideRejected>().firstOrNull()?.let { return OverrideResult.Rejected(it.reason) }
        return state.overrideUntilMillis?.let { OverrideResult.Granted(it) } ?: OverrideResult.NotRunning
    }

    /** Screen off or shutting down: write everything now. */
    suspend fun flush() = mutex.withLock {
        if (engine == null) return@withLock
        save()
        state.today?.let { recordToday(it) }
    }

    fun isArmed(): Boolean = rules?.isArmed(clock.instant()) ?: true

    /** 5 s while active; otherwise up to 30 s, or until the next activation boundary. */
    fun tickDelay(): Duration {
        if (state.active) return EngineConfig.Defaults.tickInterval
        val now = clock.instant()
        val next = rules?.changesBetween(now, now.plusMillis(INACTIVE_TICK.inWholeMilliseconds), clock.zone)?.firstOrNull()
            ?: return INACTIVE_TICK
        return (next.toEpochMilli() - now.toEpochMilli()).milliseconds.coerceAtLeast(1.seconds)
    }

    /** Null while not configured. */
    private suspend fun step(event: EngineEvent): List<EngineEffect>? = mutex.withLock {
        val engine = engine ?: return@withLock null
        val settings = checkNotNull(settings)
        val before = state
        val result = engine.handle(state, event)
        state = result.state
        val now = clock.millis()
        val effects = result.effects

        // Persist first, so the slap (which takes ~2 s) can't delay it.
        if (effects.isNotEmpty()) store.record(effects, now)
        effects.filterIsInstance<EngineEffect.ServiceGap>().forEach { store.serviceGap(now, it.duration) }
        if (effects.isNotEmpty() || importantChange(before, state) || due(lastSavedAt, now, SAVE_EVERY)) {
            save()
        }
        val today = state.today
        if (today != null && due(lastTodayAt, now, TODAY_EVERY)) recordToday(today)
        publishStatus()

        for (effect in effects) {
            when (effect) {
                is EngineEffect.Warn -> actions.warn(effect.slapAtMillis, effect.freeSnoozeAvailable)
                EngineEffect.CancelWarning -> actions.cancelWarning()
                is EngineEffect.Slap -> {
                    actions.cancelWarning()
                    actions.slap(settings.alwaysPlaySound)
                }
                is EngineEffect.OverrideRejected -> actions.overrideRejected(effect.reason)
                // Shown through the status, or stored above.
                is EngineEffect.StartLockout,
                EngineEffect.EndLockout,
                is EngineEffect.LogOverride,
                is EngineEffect.DayRolledOver,
                is EngineEffect.SessionExpired,
                is EngineEffect.ServiceGap,
                -> Unit
            }
        }
        effects
    }

    private fun due(last: Long?, now: Long, every: Duration) = last == null || now - last >= every.inWholeMilliseconds

    private suspend fun save() {
        store.saveState(state)
        lastSavedAt = clock.millis()
    }

    private suspend fun recordToday(today: DailyStats) {
        store.recordToday(today)
        lastTodayAt = clock.millis()
    }

    private fun importantChange(a: EngineState, b: EngineState) =
        a.active != b.active ||
            a.level != b.level ||
            a.lockoutUntilMillis != b.lockoutUntilMillis ||
            a.overrideUntilMillis != b.overrideUntilMillis ||
            a.warningIssued != b.warningIssued ||
            a.usedSnoozePeriods != b.usedSnoozePeriods ||
            a.reportedExpiredSessionId != b.reportedExpiredSessionId

    private fun publishStatus() {
        val config = settings?.engine ?: return
        val cap = config.dailyCap
        val capReached = cap != null && (state.today?.countedMillis ?: 0) >= cap.inWholeMilliseconds
        statusFlow.value = MonitorStatus(
            running = true,
            armed = isArmed(),
            active = state.active,
            usage = state.usage,
            foregroundPackage = state.foregroundPackage,
            streakMillis = state.streakMillis,
            thresholdMillis = (if (capReached) config.shortestLevel else config.threshold(state.level)).inWholeMilliseconds,
            level = state.level,
            lockoutUntilMillis = state.lockoutUntilMillis,
            overrideUntilMillis = state.overrideUntilMillis,
            freeSnoozeAvailable = engine?.freeSnoozeAvailable(state) ?: false,
        )
    }

    companion object {
        val SAVE_EVERY = 30.seconds
        val TODAY_EVERY = 60.seconds
        val INACTIVE_TICK = 30.seconds
    }
}
