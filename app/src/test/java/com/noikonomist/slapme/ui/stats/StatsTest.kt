package com.noikonomist.slapme.ui.stats

import com.noikonomist.slapme.data.OverrideRecord
import com.noikonomist.slapme.data.SlapRecord
import com.noikonomist.slapme.engine.DailyStats
import com.noikonomist.slapme.engine.OverrideType
import com.noikonomist.slapme.engine.SlapReason
import com.noikonomist.slapme.engine.TEST_ZONE
import com.noikonomist.slapme.engine.instantAt
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsTest {

    private val monday = LocalDate.parse("2026-10-26")

    private fun slapAt(date: String, time: String) =
        SlapRecord(instantAt(date, time).toEpochMilli(), "com.example.feed", 0, SlapReason.THRESHOLD)

    private fun overrideAt(date: String, type: OverrideType) =
        OverrideRecord(instantAt(date, "12:00:00").toEpochMilli(), type, 5.minutes)

    // --- week bounds --------------------------------------------------------------------------

    @Test
    fun weekIsTheSevenDaysEndingOnTheGivenDay() {
        val days = WeekStatsCalculator.days(monday)
        assertEquals(7, days.size)
        assertEquals(LocalDate.parse("2026-10-20"), days.first())
        assertEquals(monday, days.last())
    }

    // --- grouping and totals ------------------------------------------------------------------

    @Test
    fun emptyWeekIsAllZeros() {
        val week = WeekStatsCalculator.compute(monday, emptyList(), emptyList(), emptyList(), TEST_ZONE)
        assertEquals(7, week.days.size)
        assertEquals(0, week.totalSlaps)
        assertNull(week.busiestDay)
        assertEquals(0L, week.longestFocusMillis)
        assertEquals(0L, week.usedMillis)
    }

    @Test
    fun slapsAreCountedOnTheirLocalDay() {
        val slaps = listOf(
            slapAt("2026-10-25", "23:59:00"),
            slapAt("2026-10-26", "00:01:00"),
            slapAt("2026-10-26", "15:00:00"),
            slapAt("2026-10-20", "09:00:00"),
        )
        val week = WeekStatsCalculator.compute(monday, emptyList(), slaps, emptyList(), TEST_ZONE)

        assertEquals(listOf(1, 0, 0, 0, 0, 1, 2), week.days.map { it.slaps })
        assertEquals(4, week.totalSlaps)
        assertEquals(monday, week.busiestDay?.date)
    }

    @Test
    fun usageAndLongestFocusComeFromTheDailyRows() {
        val usage = listOf(
            DailyStats(LocalDate.parse("2026-10-22"), countedMillis = 30.minutes.inWholeMilliseconds, longestFocusMillis = 50.minutes.inWholeMilliseconds),
            DailyStats(monday, countedMillis = 10.minutes.inWholeMilliseconds, longestFocusMillis = 70.minutes.inWholeMilliseconds),
            DailyStats(LocalDate.parse("2026-10-10"), countedMillis = 99.minutes.inWholeMilliseconds, longestFocusMillis = 999.minutes.inWholeMilliseconds),
        )
        val week = WeekStatsCalculator.compute(monday, usage, emptyList(), emptyList(), TEST_ZONE)

        assertEquals(40.minutes.inWholeMilliseconds, week.usedMillis)
        assertEquals("rows outside the week are ignored", 70.minutes.inWholeMilliseconds, week.longestFocusMillis)
        assertEquals(30.minutes.inWholeMilliseconds, week.days[2].usedMillis)
    }

    @Test
    fun overridesAreSplitByType() {
        val overrides = listOf(
            overrideAt("2026-10-26", OverrideType.SNOOZE),
            overrideAt("2026-10-25", OverrideType.SNOOZE),
            overrideAt("2026-10-26", OverrideType.FRICTION),
            overrideAt("2026-10-24", OverrideType.SWITCH_OFF),
        )
        val week = WeekStatsCalculator.compute(monday, emptyList(), emptyList(), overrides, TEST_ZONE)

        assertEquals(2, week.snoozes)
        assertEquals(1, week.frictionOverrides)
        assertEquals(1, week.switchOffs)
        assertEquals(2, week.days.last().overrides)
    }

    // --- chart scale ----------------------------------------------------------------------------

    @Test
    fun axisMaxIsTheNextRoundNumber() {
        assertEquals(1.0, ChartScale.niceMax(0.0), 0.0)
        assertEquals(1.0, ChartScale.niceMax(1.0), 0.0)
        assertEquals(5.0, ChartScale.niceMax(3.0), 0.0)
        assertEquals(5.0, ChartScale.niceMax(5.0), 0.0)
        assertEquals(10.0, ChartScale.niceMax(7.0), 0.0)
        assertEquals(20.0, ChartScale.niceMax(12.0), 0.0)
        assertEquals(50.0, ChartScale.niceMax(45.0), 0.0)
        assertEquals(200.0, ChartScale.niceMax(101.0), 0.0)
        assertEquals(0.5, ChartScale.niceMax(0.3), 1e-9)
    }

    @Test
    fun gridlinesAtZeroHalfAndTop() {
        assertEquals(listOf(0.0, 5.0, 10.0), ChartScale.gridlines(10.0))
    }

    @Test
    fun usedTimeIsChartedInMinutes() {
        assertEquals(1.5, ChartMetric.TIME_USED.value(DayStats(monday, slaps = 3, usedMillis = 90.seconds.inWholeMilliseconds, longestFocusMillis = 0, overrides = 0)), 1e-9)
        assertEquals(3.0, ChartMetric.SLAPS.value(DayStats(monday, slaps = 3, usedMillis = 0, longestFocusMillis = 0, overrides = 0)), 0.0)
    }
}
