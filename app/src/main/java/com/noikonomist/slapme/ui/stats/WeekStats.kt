package com.noikonomist.slapme.ui.stats

import com.noikonomist.slapme.data.OverrideRecord
import com.noikonomist.slapme.data.SlapRecord
import com.noikonomist.slapme.engine.DailyStats
import com.noikonomist.slapme.engine.OverrideType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class DayStats(
    val date: LocalDate,
    val slaps: Int,
    val usedMillis: Long,
    val longestFocusMillis: Long,
    val overrides: Int,
)

data class WeekStats(
    /** Oldest first, seven days. */
    val days: List<DayStats>,
    val totalSlaps: Int,
    /** The day with the most slaps, or null without slaps. */
    val busiestDay: DayStats?,
    /** Longest stretch of active time without a slap. */
    val longestFocusMillis: Long,
    val usedMillis: Long,
    val snoozes: Int,
    val frictionOverrides: Int,
    val switchOffs: Int,
)

object WeekStatsCalculator {

    fun days(endDate: LocalDate): List<LocalDate> = (6L downTo 0L).map { endDate.minusDays(it) }

    fun compute(
        endDate: LocalDate,
        usage: List<DailyStats>,
        slaps: List<SlapRecord>,
        overrides: List<OverrideRecord>,
        zone: ZoneId,
    ): WeekStats {
        val dates = days(endDate)
        fun dateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val slapsByDay = slaps.groupingBy { dateOf(it.timestampMillis) }.eachCount()
        val overridesByDay = overrides.groupingBy { dateOf(it.timestampMillis) }.eachCount()
        val usageByDay = usage.associateBy { it.date }
        val inWeek = overrides.filter { dateOf(it.timestampMillis) in dates }

        val dayStats = dates.map { date ->
            DayStats(
                date = date,
                slaps = slapsByDay[date] ?: 0,
                usedMillis = usageByDay[date]?.countedMillis ?: 0,
                longestFocusMillis = usageByDay[date]?.longestFocusMillis ?: 0,
                overrides = overridesByDay[date] ?: 0,
            )
        }
        return WeekStats(
            days = dayStats,
            totalSlaps = dayStats.sumOf { it.slaps },
            busiestDay = dayStats.filter { it.slaps > 0 }.maxByOrNull { it.slaps },
            longestFocusMillis = dayStats.maxOf { it.longestFocusMillis },
            usedMillis = dayStats.sumOf { it.usedMillis },
            snoozes = inWeek.count { it.type == OverrideType.SNOOZE },
            frictionOverrides = inWeek.count { it.type == OverrideType.FRICTION },
            switchOffs = inWeek.count { it.type == OverrideType.SWITCH_OFF },
        )
    }
}

enum class ChartMetric {
    SLAPS,

    /** In minutes. */
    TIME_USED,
    ;

    fun value(day: DayStats): Double = when (this) {
        SLAPS -> day.slaps.toDouble()
        TIME_USED -> day.usedMillis / 60_000.0
    }
}

object ChartScale {
    /** The smallest 1, 2 or 5 × 10ⁿ at or above [max]; 1 for an empty chart. */
    fun niceMax(max: Double): Double {
        if (max <= 0.0) return 1.0
        val magnitude = 10.0.pow(floor(log10(max)))
        val step = listOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= max - 1e-9 }
        return step * magnitude
    }

    fun gridlines(max: Double): List<Double> = listOf(0.0, max / 2, max)
}
