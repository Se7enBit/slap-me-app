package com.noikonomist.slapme.ui.schedules

import com.noikonomist.slapme.engine.DaysMask
import com.noikonomist.slapme.engine.ScheduleBlock
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockDraftTest {

    @Test
    fun newDraftIsWorkHoursOnWeekdaysAndValid() {
        val draft = BlockDraft.NEW
        assertEquals(DaysMask.days(DaysMask.of(*java.time.DayOfWeek.entries.take(5).toTypedArray())), draft.days)
        assertEquals(9 * 60, draft.startMinute)
        assertEquals(17 * 60, draft.endMinute)
        assertTrue(draft.errors.isEmpty())
    }

    @Test
    fun needsADayAndDifferentTimes() {
        assertEquals(setOf(BlockError.NO_DAYS), BlockDraft.NEW.copy(days = emptySet()).errors)
        assertEquals(setOf(BlockError.SAME_TIMES), BlockDraft.NEW.copy(startMinute = 600, endMinute = 600).errors)
        assertTrue("all day ignores the times", BlockDraft.NEW.copy(startMinute = 600, endMinute = 600, allDay = true).errors.isEmpty())
    }

    @Test
    fun crossingMidnightIsDetected() {
        assertTrue(BlockDraft.NEW.copy(startMinute = 22 * 60, endMinute = 2 * 60).crossesMidnight)
        assertFalse(BlockDraft.NEW.crossesMidnight)
        assertFalse(BlockDraft.NEW.copy(startMinute = 22 * 60, endMinute = 2 * 60, allDay = true).crossesMidnight)
    }

    @Test
    fun convertsToAndFromBlocks() {
        val block = ScheduleBlock(7, DaysMask.of(MONDAY, FRIDAY), 22 * 60, 2 * 60, enabled = false)
        val draft = BlockDraft.from(block)
        assertEquals(setOf(MONDAY, FRIDAY), draft.days)
        assertFalse(draft.allDay)
        assertEquals(block, draft.toBlock())
    }

    @Test
    fun allDayIsStoredAsMidnightToMidnight() {
        val draft = BlockDraft.NEW.copy(allDay = true, startMinute = 600, endMinute = 700)
        val block = draft.toBlock()
        assertEquals(0, block.startMinute)
        assertEquals(24 * 60, block.endMinute)
        assertTrue(BlockDraft.from(block).allDay)
    }
}
