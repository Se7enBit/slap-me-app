package com.noikonomist.slapme.ui.schedules

import com.noikonomist.slapme.engine.DaysMask
import com.noikonomist.slapme.engine.ScheduleBlock
import java.time.DayOfWeek

enum class BlockError { NO_DAYS, SAME_TIMES }

/** A schedule block being edited. "All day" is stored as 00:00–24:00. */
data class BlockDraft(
    val id: Long = 0,
    val days: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
    val allDay: Boolean = false,
    val enabled: Boolean = true,
) {
    val errors: Set<BlockError>
        get() = buildSet {
            if (days.isEmpty()) add(BlockError.NO_DAYS)
            if (!allDay && startMinute == endMinute) add(BlockError.SAME_TIMES)
        }

    val crossesMidnight: Boolean get() = !allDay && endMinute < startMinute

    fun toBlock() = ScheduleBlock(
        id = id,
        daysMask = DaysMask.of(*days.toTypedArray()),
        startMinute = if (allDay) 0 else startMinute,
        endMinute = if (allDay) MINUTES_PER_DAY else endMinute,
        enabled = enabled,
    )

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60

        val NEW = BlockDraft(
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            startMinute = 9 * 60,
            endMinute = 17 * 60,
        )

        fun from(block: ScheduleBlock): BlockDraft {
            val allDay = block.startMinute == 0 && block.endMinute == MINUTES_PER_DAY
            return BlockDraft(
                id = block.id,
                days = DaysMask.days(block.daysMask),
                startMinute = if (allDay) NEW.startMinute else block.startMinute,
                endMinute = if (allDay) NEW.endMinute else block.endMinute,
                allDay = allDay,
                enabled = block.enabled,
            )
        }
    }
}
