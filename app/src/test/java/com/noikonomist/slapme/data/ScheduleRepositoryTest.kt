package com.noikonomist.slapme.data

import android.app.Application
import com.noikonomist.slapme.engine.DaysMask
import com.noikonomist.slapme.engine.ScheduleBlock
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ScheduleRepositoryTest {

    private val db = inMemoryDatabase()
    private val repo = ScheduleRepository(db.scheduleBlockDao())

    @After
    fun close() = db.close()

    @Test
    fun addedBlockIsListedWithItsNewId() = runTest {
        val id = repo.add(ScheduleBlock(0, DaysMask.of(MONDAY, FRIDAY), 9 * 60, 17 * 60))

        assertTrue(id > 0)
        assertEquals(
            listOf(ScheduleBlock(id, DaysMask.of(MONDAY, FRIDAY), 9 * 60, 17 * 60, enabled = true)),
            repo.blocks.first(),
        )
    }

    @Test
    fun midnightCrossingAndDisabledBlocksAreStoredAsIs() = runTest {
        val id = repo.add(ScheduleBlock(0, DaysMask.of(FRIDAY), 22 * 60, 2 * 60, enabled = false))
        assertEquals(ScheduleBlock(id, DaysMask.of(FRIDAY), 22 * 60, 2 * 60, enabled = false), repo.blocks.first().single())
    }

    @Test
    fun updateAndDelete() = runTest {
        val id = repo.add(ScheduleBlock(0, DaysMask.ALL, 9 * 60, 12 * 60))
        repo.update(ScheduleBlock(id, DaysMask.of(MONDAY), 10 * 60, 11 * 60, enabled = false))
        assertEquals(ScheduleBlock(id, DaysMask.of(MONDAY), 10 * 60, 11 * 60, enabled = false), repo.blocks.first().single())

        repo.delete(id)
        assertEquals(emptyList<ScheduleBlock>(), repo.blocks.first())
    }

    @Test
    fun blocksAreOrderedByStartTime() = runTest {
        repo.add(ScheduleBlock(0, DaysMask.ALL, 14 * 60, 15 * 60))
        repo.add(ScheduleBlock(0, DaysMask.ALL, 8 * 60, 9 * 60))
        assertEquals(listOf(8 * 60, 14 * 60), repo.blocks.first().map { it.startMinute })
    }
}
