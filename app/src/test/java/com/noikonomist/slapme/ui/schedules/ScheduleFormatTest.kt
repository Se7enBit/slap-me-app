package com.noikonomist.slapme.ui.schedules

import com.noikonomist.slapme.engine.DaysMask
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleFormatTest {

    private fun days(vararg d: java.time.DayOfWeek) = ScheduleFormat.days(DaysMask.of(*d))

    @Test
    fun namedDaySets() {
        assertEquals(DaySummary.EveryDay, ScheduleFormat.days(DaysMask.ALL))
        assertEquals(DaySummary.Weekdays, days(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY))
        assertEquals(DaySummary.Weekend, days(SATURDAY, SUNDAY))
    }

    @Test
    fun customDaysGroupRunsOfThreeOrMore() {
        val us = Locale.US
        assertEquals("Mon, Wed, Fri", (days(MONDAY, WEDNESDAY, FRIDAY) as DaySummary.Custom).label(us))
        assertEquals("Mon–Thu", (days(MONDAY, TUESDAY, WEDNESDAY, THURSDAY) as DaySummary.Custom).label(us))
        assertEquals("Mon, Tue", (days(MONDAY, TUESDAY) as DaySummary.Custom).label(us))
        assertEquals("Mon–Wed, Fri, Sun", (days(MONDAY, TUESDAY, WEDNESDAY, FRIDAY, SUNDAY) as DaySummary.Custom).label(us))
        assertEquals("Sat", (days(SATURDAY) as DaySummary.Custom).label(us))
    }

    @Test
    fun timesIn24And12HourFormat() {
        assertEquals("09:05", ScheduleFormat.time(9 * 60 + 5, is24Hour = true, Locale.US))
        assertEquals("24:00", ScheduleFormat.time(24 * 60, is24Hour = true, Locale.US))
        assertEquals("9:05 AM", ScheduleFormat.time(9 * 60 + 5, is24Hour = false, Locale.US))
        assertEquals("12:00 AM", ScheduleFormat.time(24 * 60, is24Hour = false, Locale.US))
        assertEquals("10:30 PM", ScheduleFormat.time(22 * 60 + 30, is24Hour = false, Locale.US))
    }
}
