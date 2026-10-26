package com.noikonomist.slapme.engine

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextActivationTest {

    private fun block(start: Int, end: Int, mask: Int = DaysMask.ALL, enabled: Boolean = true, id: Long = 1) =
        ScheduleBlock(id, mask, start, end, enabled)

    private fun next(rules: ActivationRules, date: String, time: String) =
        rules.nextActivation(instantAt(date, time), TEST_ZONE)

    @Test
    fun nothingArmedHasNoNextActivation() {
        assertNull(next(ActivationRules(), "2026-10-26", "10:00:00"))
    }

    @Test
    fun activeNowIsNow() {
        val now = instantAt("2026-10-26", "10:00:00")
        assertEquals(now, ActivationRules(alwaysOn = true).nextActivation(now, TEST_ZONE))
        assertEquals(now, ActivationRules(blocks = listOf(block(9 * 60, 17 * 60))).nextActivation(now, TEST_ZONE))
    }

    @Test
    fun laterTodayOrTomorrow() {
        val rules = ActivationRules(blocks = listOf(block(14 * 60, 15 * 60)))
        assertEquals(instantAt("2026-10-26", "14:00:00"), next(rules, "2026-10-26", "10:00:00"))
        assertEquals(instantAt("2026-10-27", "14:00:00"), next(rules, "2026-10-26", "16:00:00"))
    }

    @Test
    fun earliestOfSeveralBlocks() {
        val rules = ActivationRules(blocks = listOf(block(14 * 60, 15 * 60, id = 1), block(11 * 60, 12 * 60, id = 2)))
        assertEquals(instantAt("2026-10-26", "11:00:00"), next(rules, "2026-10-26", "10:00:00"))
    }

    @Test
    fun weeklyBlockAlmostAWeekAway() {
        // Tuesday evening; the only block is Monday morning.
        val rules = ActivationRules(blocks = listOf(block(9 * 60, 10 * 60, mask = DaysMask.of(MONDAY))))
        assertEquals(instantAt("2026-11-02", "09:00:00"), next(rules, "2026-10-27", "18:00:00"))
    }

    @Test
    fun midnightCrossingBlockStartsOnItsDay() {
        val rules = ActivationRules(blocks = listOf(block(22 * 60, 2 * 60, mask = DaysMask.of(FRIDAY))))
        assertEquals(instantAt("2026-10-30", "22:00:00"), next(rules, "2026-10-30", "21:00:00"))
    }

    @Test
    fun disabledAndInvalidBlocksNeverActivate() {
        val rules = ActivationRules(blocks = listOf(block(14 * 60, 15 * 60, enabled = false), block(9 * 60, 9 * 60, id = 2)))
        assertNull(next(rules, "2026-10-26", "10:00:00"))
    }
}
