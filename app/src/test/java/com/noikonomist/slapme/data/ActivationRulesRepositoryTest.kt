package com.noikonomist.slapme.data

import android.app.Application
import com.noikonomist.slapme.data.settings.SettingsRepository
import com.noikonomist.slapme.engine.ActivationRules
import com.noikonomist.slapme.engine.DaysMask
import com.noikonomist.slapme.engine.MONDAY_10AM
import com.noikonomist.slapme.engine.MutableClock
import com.noikonomist.slapme.engine.ScheduleBlock
import com.noikonomist.slapme.engine.TEST_ZONE
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ActivationRulesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val db = inMemoryDatabase()
    private val clock = MutableClock(MONDAY_10AM, TEST_ZONE)
    private val schedules = ScheduleRepository(db.scheduleBlockDao())
    private val sessions = FocusSessionRepository(db.focusSessionDao(), clock)

    @After
    fun close() = db.close()

    @Test
    fun rulesFollowSchedulesSessionsAndAlwaysOn() = runTest {
        val settings = SettingsRepository(testPreferencesStore(backgroundScope, tmp, "settings"))
        val repo = ActivationRulesRepository(schedules, sessions, settings)

        assertEquals(ActivationRules(), repo.rules.first())

        val id = schedules.add(ScheduleBlock(0, DaysMask.ALL, 9 * 60, 17 * 60))
        assertEquals(listOf(ScheduleBlock(id, DaysMask.ALL, 9 * 60, 17 * 60)), repo.rules.first().blocks)

        val session = sessions.start(25.minutes)
        assertEquals(session, repo.rules.first().session)

        settings.update { it.copy(alwaysOn = true) }
        assertTrue(repo.rules.first().alwaysOn)

        sessions.stop()
        assertNull(repo.rules.first().session)
    }
}
