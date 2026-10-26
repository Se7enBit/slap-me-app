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

    fun days(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filterTo(mutableSetOf()) { contains(mask, it) }

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

    internal fun runsOn(date: LocalDate) = DaysMask.contains(daysMask, date.dayOfWeek)

    internal fun startOn(date: LocalDate, zone: ZoneId): Instant = date.atMinute(startMinute, zone)

    internal fun endOn(date: LocalDate, zone: ZoneId): Instant =
        (if (startMinute < endMinute) date else date.plusDays(1)).atMinute(endMinute, zone)

    private fun LocalDate.atMinute(minute: Int, zone: ZoneId): Instant =
        atStartOfDay().plusMinutes(minute.toLong()).atZone(zone).toInstant()

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

    /** When the app next becomes active: [now] if it already is, null if nothing will activate it. */
    fun nextActivation(now: Instant, zone: ZoneId): Instant? {
        if (isActive(now, zone)) return now
        val candidates = mutableListOf<Instant>()
        session?.let { s -> Instant.ofEpochMilli(s.startMillis).takeIf { it > now && s.isRunning(s.startMillis) }?.let(candidates::add) }
        val today = now.atZone(zone).toLocalDate()
        for (block in blocks.filter { it.enabled && it.isValid }) {
            // A week ahead covers every weekly block.
            for (offset in 0L..7L) {
                val date = today.plusDays(offset)
                if (block.runsOn(date)) block.startOn(date, zone).takeIf { it > now }?.let(candidates::add)
            }
        }
        return candidates.minOrNull()
    }

    /** Instants strictly between [from] and [to] where [isActive] may change. */
    internal fun changesBetween(from: Instant, to: Instant, zone: ZoneId): List<Instant> {
        val points = sortedSetOf<Instant>()
        session?.let { s ->
            points += Instant.ofEpochMilli(s.startMillis)
            s.plannedEndMillis?.let { points += Instant.ofEpochMilli(it) }
        }
        val lastDate = to.atZone(zone).toLocalDate()
        for (block in blocks.filter { it.enabled && it.isValid }) {
            // Start a day early: yesterday's midnight-crossing occurrence may end inside the interval.
            var date = from.atZone(zone).toLocalDate().minusDays(1)
            while (!date.isAfter(lastDate)) {
                if (block.runsOn(date)) {
                    points += block.startOn(date, zone)
                    points += block.endOn(date, zone)
                }
                date = date.plusDays(1)
            }
        }
        return points.filter { it > from && it < to }
    }
}
