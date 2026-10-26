package com.noikonomist.slapme.ui.schedules

import com.noikonomist.slapme.engine.DaysMask
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

sealed interface DaySummary {
    data object EveryDay : DaySummary
    data object Weekdays : DaySummary
    data object Weekend : DaySummary

    /** Runs of three or more consecutive days become ranges ("Mon–Thu"), the rest are listed. */
    data class Custom(val days: List<DayOfWeek>) : DaySummary {
        fun label(locale: Locale): String {
            fun name(day: DayOfWeek) = day.getDisplayName(TextStyle.SHORT, locale)
            val runs = mutableListOf<MutableList<DayOfWeek>>()
            for (day in days.sorted()) {
                val run = runs.lastOrNull()
                if (run != null && run.last().value + 1 == day.value) run += day else runs += mutableListOf(day)
            }
            return runs.joinToString(", ") { run ->
                if (run.size >= 3) "${name(run.first())}–${name(run.last())}" else run.joinToString(", ") { name(it) }
            }
        }
    }
}

object ScheduleFormat {
    private val WEEKDAYS = DaysMask.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val WEEKEND = DaysMask.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    fun days(mask: Int): DaySummary = when (mask and DaysMask.ALL) {
        DaysMask.ALL -> DaySummary.EveryDay
        WEEKDAYS -> DaySummary.Weekdays
        WEEKEND -> DaySummary.Weekend
        else -> DaySummary.Custom(DaysMask.days(mask).sorted())
    }

    /** Minutes since midnight as a clock time; 1440 is "24:00" (or midnight in 12-hour format). */
    fun time(minute: Int, is24Hour: Boolean, locale: Locale): String {
        if (is24Hour) return "%02d:%02d".format(Locale.ROOT, minute / 60, minute % 60)
        val time = LocalTime.of((minute / 60) % 24, minute % 60)
        return time.format(DateTimeFormatter.ofPattern("h:mm a", locale))
    }
}
