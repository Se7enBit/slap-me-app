package com.noikonomist.slapme.engine

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationRulesTest {

    private val zone = ZoneOffset.UTC

    // 2026-10-26 is a Monday.
    private fun at(date: String, time: String) = LocalDateTime.parse("${date}T$time").toInstant(zone)

    private fun block(
        start: String,
        end: String,
        vararg days: java.time.DayOfWeek,
        id: Long = 1,
        enabled: Boolean = true,
    ) = ScheduleBlock(id, DaysMask.of(*days), minuteOf(start), minuteOf(end), enabled)

    private fun minuteOf(hhmm: String): Int {
        val (h, m) = hhmm.split(":").map(String::toInt)
        return h * 60 + m
    }

    @Test
    fun weekdayBlockIsActiveInsideItsHoursOnly() {
        val rules = ActivationRules(blocks = listOf(block("09:00", "17:00", MONDAY)))

        assertFalse(rules.isActive(at("2026-10-26", "08:59"), zone))
        assertTrue(rules.isActive(at("2026-10-26", "09:00"), zone))
        assertTrue(rules.isActive(at("2026-10-26", "16:59"), zone))
        assertFalse(rules.isActive(at("2026-10-26", "17:00"), zone))
        assertFalse("Tuesday is not in the mask", rules.isActive(at("2026-10-27", "10:00"), zone))
    }

    @Test
    fun disabledBlockIsNeverActive() {
        val rules = ActivationRules(blocks = listOf(block("09:00", "17:00", MONDAY, enabled = false)))

        assertFalse(rules.isActive(at("2026-10-26", "10:00"), zone))
        assertFalse(rules.isArmed(at("2026-10-26", "10:00")))
    }

    @Test
    fun anyOfSeveralBlocksActivates() {
        val rules = ActivationRules(
            blocks = listOf(
                block("09:00", "12:00", MONDAY, id = 1),
                block("13:00", "17:00", MONDAY, id = 2),
            ),
        )

        assertTrue(rules.isActive(at("2026-10-26", "10:00"), zone))
        assertFalse(rules.isActive(at("2026-10-26", "12:30"), zone))
        assertEquals(
            ActivePeriod.Block(2, LocalDate.parse("2026-10-26")),
            rules.currentPeriod(at("2026-10-26", "14:00"), zone),
        )
    }

    @Test
    fun midnightCrossingBlockBelongsToTheDayItStarts() {
        // Friday 22:00 -> Saturday 02:00
        val rules = ActivationRules(blocks = listOf(block("22:00", "02:00", FRIDAY)))

        assertFalse(rules.isActive(at("2026-10-30", "21:59"), zone)) // Fri
        assertTrue(rules.isActive(at("2026-10-30", "23:30"), zone)) // Fri
        assertTrue(rules.isActive(at("2026-10-31", "01:59"), zone)) // Sat early morning, still Friday's block
        assertFalse(rules.isActive(at("2026-10-31", "02:00"), zone))
        assertFalse("Saturday 23:00 is not a Friday block", rules.isActive(at("2026-10-31", "23:00"), zone))
        assertFalse("Friday 01:00 belongs to Thursday's block", rules.isActive(at("2026-10-30", "01:00"), zone))
    }

    @Test
    fun midnightCrossingOccurrenceKeepsItsStartDate() {
        val rules = ActivationRules(blocks = listOf(block("22:00", "02:00", FRIDAY, id = 7)))
        val friday = LocalDate.parse("2026-10-30")

        assertEquals(ActivePeriod.Block(7, friday), rules.currentPeriod(at("2026-10-30", "23:00"), zone))
        assertEquals(ActivePeriod.Block(7, friday), rules.currentPeriod(at("2026-10-31", "01:00"), zone))
    }

    @Test
    fun sundayNightBlockCrossesIntoMonday() {
        val rules = ActivationRules(blocks = listOf(block("23:00", "01:00", SUNDAY)))

        assertTrue(rules.isActive(at("2026-11-01", "23:30"), zone)) // Sun
        assertTrue(rules.isActive(at("2026-11-02", "00:30"), zone)) // Mon
        assertFalse("Sunday 00:30 follows Saturday", rules.isActive(at("2026-11-01", "00:30"), zone))
        assertFalse("Tuesday 00:30 follows Monday", rules.isActive(at("2026-11-03", "00:30"), zone))
    }

    @Test
    fun blockEndingAtMidnightUsesMinute1440() {
        val rules = ActivationRules(blocks = listOf(block("00:00", "24:00", SATURDAY)))

        assertTrue(rules.isActive(at("2026-10-31", "00:00"), zone))
        assertTrue(rules.isActive(at("2026-10-31", "23:59"), zone))
        assertFalse(rules.isActive(at("2026-11-01", "00:00"), zone))
    }

    @Test
    fun blockWithEqualStartAndEndIsInvalidAndNeverActive() {
        val b = block("09:00", "09:00", MONDAY)
        val rules = ActivationRules(blocks = listOf(b))

        assertFalse(b.isValid)
        assertFalse(rules.isActive(at("2026-10-26", "09:00"), zone))
        assertFalse(rules.isActive(at("2026-10-26", "15:00"), zone))
        assertFalse(rules.isArmed(at("2026-10-26", "15:00")))
    }

    @Test
    fun openEndedSessionIsActiveFromItsStart() {
        val start = at("2026-10-26", "10:00")
        val rules = ActivationRules(session = FocusSession(3, start.toEpochMilli(), plannedEndMillis = null))

        assertFalse(rules.isActive(at("2026-10-26", "09:59"), zone))
        assertTrue(rules.isActive(at("2026-10-26", "10:00"), zone))
        assertTrue(rules.isActive(at("2026-10-27", "10:00"), zone))
        assertEquals(ActivePeriod.Session(3), rules.currentPeriod(at("2026-10-26", "11:00"), zone))
    }

    @Test
    fun plannedSessionEndsAtItsPlannedEnd() {
        val rules = ActivationRules(
            session = FocusSession(
                id = 3,
                startMillis = at("2026-10-26", "10:00").toEpochMilli(),
                plannedEndMillis = at("2026-10-26", "10:25").toEpochMilli(),
            ),
        )

        assertTrue(rules.isActive(at("2026-10-26", "10:24"), zone))
        assertFalse(rules.isActive(at("2026-10-26", "10:25"), zone))
        assertFalse(rules.isArmed(at("2026-10-26", "10:25")))
        assertTrue(rules.isArmed(at("2026-10-26", "10:24")))
    }

    @Test
    fun alwaysOnIsActiveAllDayWithADailyPeriod() {
        val rules = ActivationRules(alwaysOn = true)

        assertTrue(rules.isActive(at("2026-10-26", "03:00"), zone))
        assertEquals(
            ActivePeriod.AlwaysOn(LocalDate.parse("2026-10-27")),
            rules.currentPeriod(at("2026-10-27", "08:00"), zone),
        )
    }

    @Test
    fun periodPriorityIsSessionThenBlockThenAlwaysOn() {
        val now = at("2026-10-26", "10:00")
        val session = FocusSession(9, at("2026-10-26", "09:30").toEpochMilli(), null)
        val blocks = listOf(block("09:00", "17:00", MONDAY, id = 4))

        assertEquals(
            ActivePeriod.Session(9),
            ActivationRules(blocks, session, alwaysOn = true).currentPeriod(now, zone),
        )
        assertEquals(
            ActivePeriod.Block(4, LocalDate.parse("2026-10-26")),
            ActivationRules(blocks, null, alwaysOn = true).currentPeriod(now, zone),
        )
        assertEquals(
            ActivePeriod.AlwaysOn(LocalDate.parse("2026-10-26")),
            ActivationRules(emptyList(), null, alwaysOn = true).currentPeriod(now, zone),
        )
    }

    @Test
    fun periodKeysAreDistinctAndStable() {
        val day = LocalDate.parse("2026-10-26")
        val keys = setOf(
            ActivePeriod.Session(1).key,
            ActivePeriod.Block(1, day).key,
            ActivePeriod.Block(1, day.plusDays(1)).key,
            ActivePeriod.AlwaysOn(day).key,
        )
        assertEquals(4, keys.size)
        assertEquals("block:1:2026-10-26", ActivePeriod.Block(1, day).key)
    }

    @Test
    fun armedWhenAnyModeIsEnabledEvenIfNotActiveNow() {
        val outside = at("2026-10-26", "20:00")

        assertTrue(ActivationRules(blocks = listOf(block("09:00", "17:00", TUESDAY))).isArmed(outside))
        assertTrue(ActivationRules(alwaysOn = true).isArmed(outside))
        assertFalse(ActivationRules().isArmed(outside))
        assertNull(ActivationRules().currentPeriod(outside, zone))
    }

    @Test
    fun daysMaskRoundTrips() {
        val mask = DaysMask.of(MONDAY, FRIDAY, SUNDAY)
        assertTrue(DaysMask.contains(mask, MONDAY))
        assertTrue(DaysMask.contains(mask, SUNDAY))
        assertFalse(DaysMask.contains(mask, TUESDAY))
        assertEquals(0b1111111, DaysMask.ALL)
    }
}
