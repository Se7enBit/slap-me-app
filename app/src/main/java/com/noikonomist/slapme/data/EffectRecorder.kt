package com.noikonomist.slapme.data

import com.noikonomist.slapme.data.db.DailyUsageDao
import com.noikonomist.slapme.data.db.DailyUsageEntity
import com.noikonomist.slapme.data.db.FocusSessionDao
import com.noikonomist.slapme.data.db.OverrideEventDao
import com.noikonomist.slapme.data.db.OverrideEventEntity
import com.noikonomist.slapme.data.db.SlapEventDao
import com.noikonomist.slapme.data.db.SlapEventEntity
import com.noikonomist.slapme.engine.DailyStats
import com.noikonomist.slapme.engine.EngineEffect

/** Writes the loggable engine effects to the database. Effects with no data side are ignored. */
class EffectRecorder(
    private val slaps: SlapEventDao,
    private val overrides: OverrideEventDao,
    private val dailyUsage: DailyUsageDao,
    private val sessions: FocusSessionDao,
) {
    /** [atMillis] is the time of the engine step that produced [effects]. */
    suspend fun record(effects: List<EngineEffect>, atMillis: Long) {
        for (effect in effects) {
            when (effect) {
                is EngineEffect.Slap ->
                    slaps.insert(SlapEventEntity(timestamp = atMillis, packageName = effect.packageName, level = effect.level, reason = effect.reason))

                is EngineEffect.LogOverride ->
                    overrides.insert(OverrideEventEntity(timestamp = atMillis, type = effect.type, durationSeconds = effect.duration.inWholeSeconds))

                is EngineEffect.DayRolledOver -> recordToday(effect.stats)

                is EngineEffect.SessionExpired -> sessions.endAtPlannedEnd(effect.sessionId, atMillis)

                is EngineEffect.Warn,
                EngineEffect.CancelWarning,
                is EngineEffect.StartLockout,
                EngineEffect.EndLockout,
                is EngineEffect.OverrideRejected,
                is EngineEffect.ServiceGap,
                -> Unit
            }
        }
    }

    /** Keeps the current day's row up to date before its rollover. */
    suspend fun recordToday(stats: DailyStats) {
        dailyUsage.upsert(DailyUsageEntity(stats.date, stats.countedMillis / 1_000, stats.longestFocusMillis / 1_000))
    }
}
