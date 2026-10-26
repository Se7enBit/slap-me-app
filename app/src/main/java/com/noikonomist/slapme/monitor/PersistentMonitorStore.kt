package com.noikonomist.slapme.monitor

import com.noikonomist.slapme.data.EffectRecorder
import com.noikonomist.slapme.data.settings.DiagnosticsRepository
import com.noikonomist.slapme.data.settings.EngineStateRepository
import com.noikonomist.slapme.engine.DailyStats
import com.noikonomist.slapme.engine.EngineEffect
import com.noikonomist.slapme.engine.EngineState
import com.noikonomist.slapme.util.SlapLog
import kotlin.time.Duration

/** [MonitorStore] on DataStore + Room. A failed write is logged and never stops the monitor. */
class PersistentMonitorStore(
    private val engineState: EngineStateRepository,
    private val recorder: EffectRecorder,
    private val diagnostics: DiagnosticsRepository,
) : MonitorStore {

    override suspend fun saveState(state: EngineState) = guarded("save engine state") { engineState.save(state) }

    override suspend fun record(effects: List<EngineEffect>, atMillis: Long) =
        guarded("record effects") { recorder.record(effects, atMillis) }

    override suspend fun recordToday(stats: DailyStats) = guarded("record today") { recorder.recordToday(stats) }

    override suspend fun serviceGap(atMillis: Long, duration: Duration) {
        SlapLog.w("Monitor was not running for $duration (killed?)")
        guarded("record service gap") { diagnostics.recordServiceGap(atMillis, duration) }
    }

    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SlapLog.e("Monitor failed to $what", e)
        }
    }
}
