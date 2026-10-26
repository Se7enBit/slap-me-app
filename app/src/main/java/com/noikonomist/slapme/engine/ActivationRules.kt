package com.noikonomist.slapme.engine

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Bit set of weekdays: bit 0 = Monday … bit 6 = Sunday. Same encoding as the Room column. */
object DaysMask {
    const val ALL = 0b1111111

    fun of(vararg days: DayOfWeek): Int = days.fold(0) { mask, day -> mask or bit(day) }

    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0

    private fun bit(day: DayOfWeek) = 1 shl (day.value - 1)
}

/**
 * A recurring weekly block. [startMinute] is 0..1439 and [endMinute] 0..1440 (minutes since local
 * midnight). If the end is before the start, the block crosses midnight and [daysMask] refers to the
 * day it starts. Equal start and end is invalid and never active.
 */
data class ScheduleBlock(
    val id: Long,
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean = true,
) {
    val isValid: Boolean
        get() = startMinute in 0 until MINUTES_PER_DAY &&
            endMinute in 0..MINUTES_PER_DAY &&
            startMinute != endMinute &&
            daysMask and DaysMask.ALL != 0

    /** Start date of the occurrence covering [date] at [minute], or null if none does. */
    internal fun occurrenceStart(date: LocalDate, minute: Int): LocalDate? {
        if (!enabled || !isValid) return null
        return if (startMinute < endMinute) {
            date.takeIf { runsOn(it) && minute >= startMinute && minute < endMinute }
        } else {
            when {
                minute >= startMinute && runsOn(date) -> date
                minute < endMinute && runsOn(date.minusDays(1)) -> date.minusDays(1)
                else -> null
            }
        }
    }

    private fun runsOn(date: LocalDate) = DaysMask.contains(daysMask, date.dayOfWeek)

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/** A running focus session (not yet stopped). Open-ended when [plannedEndMillis] is null. */
data class FocusSession(
    val id: Long,
    val startMillis: Long,
    val plannedEndMillis: Long?,
) {
    fun isRunning(nowMillis: Long): Boolean =
        nowMillis >= startMillis && (plannedEndMillis == null || nowMillis < plannedEndMillis)
}

/** The period the app is active in. Each gets one free snooze, keyed by [key]. */
sealed interface ActivePeriod {
    val key: String

    data class Session(val sessionId: Long) : ActivePeriod {
        override val key get() = "session:$sessionId"
    }

    data class Block(val blockId: Long, val startDate: LocalDate) : ActivePeriod {
        override val key get() = "block:$blockId:$startDate"
    }

    data class AlwaysOn(val date: LocalDate) : ActivePeriod {
        override val key get() = "day:$date"
    }
}

data class ActivationRules(
    val blocks: List<ScheduleBlock> = emptyList(),
    val session: FocusSession? = null,
    val alwaysOn: Boolean = false,
) {
    /** Armed = some mode is enabled, so the monitor must run, even if nothing is active right now. */
    fun isArmed(now: Instant): Boolean =
        alwaysOn ||
            blocks.any { it.enabled && it.isValid } ||
            session?.isRunning(now.toEpochMilli()) == true

    fun isActive(now: Instant, zone: ZoneId): Boolean = currentPeriod(now, zone) != null

    /** Priority: focus session, then schedule block, then always-on. */
    fun currentPeriod(now: Instant, zone: ZoneId): ActivePeriod? {
        session?.takeIf { it.isRunning(now.toEpochMilli()) }?.let { return ActivePeriod.Session(it.id) }

        val local = now.atZone(zone)
        val minute = local.hour * 60 + local.minute
        blocks.firstNotNullOfOrNull { block ->
            block.occurrenceStart(local.toLocalDate(), minute)?.let { ActivePeriod.Block(block.id, it) }
        }?.let { return it }

        return if (alwaysOn) ActivePeriod.AlwaysOn(local.toLocalDate()) else null
    }
}
