package com.noikonomist.slapme.data

import com.noikonomist.slapme.data.db.DailyUsageDao
import com.noikonomist.slapme.data.db.OverrideEventDao
import com.noikonomist.slapme.data.db.SlapEventDao
import com.noikonomist.slapme.engine.DailyStats
import com.noikonomist.slapme.engine.OverrideType
import com.noikonomist.slapme.engine.SlapReason
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SlapRecord(val timestampMillis: Long, val packageName: String?, val level: Int, val reason: SlapReason)

data class OverrideRecord(val timestampMillis: Long, val type: OverrideType, val duration: Duration)

/** Read side of the logs, for the stats screen. Time ranges are [from, to). */
class HistoryRepository(
    private val slaps: SlapEventDao,
    private val overrides: OverrideEventDao,
    private val dailyUsage: DailyUsageDao,
) {
    fun slaps(fromMillis: Long, toMillis: Long): Flow<List<SlapRecord>> =
        slaps.observeBetween(fromMillis, toMillis).map { rows ->
            rows.map { SlapRecord(it.timestamp, it.packageName, it.level, it.reason) }
        }

    fun overrides(fromMillis: Long, toMillis: Long): Flow<List<OverrideRecord>> =
        overrides.observeBetween(fromMillis, toMillis).map { rows ->
            rows.map { OverrideRecord(it.timestamp, it.type, it.durationSeconds.seconds) }
        }

    /** Both dates inclusive. Stored with second precision. */
    fun dailyUsage(from: LocalDate, to: LocalDate): Flow<List<DailyStats>> =
        dailyUsage.observeBetween(from, to).map { rows ->
            rows.map { DailyStats(it.date, it.countedSeconds * 1_000, it.longestFocusSeconds * 1_000) }
        }
}
