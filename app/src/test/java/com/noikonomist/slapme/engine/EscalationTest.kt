package com.noikonomist.slapme.engine

import com.noikonomist.slapme.engine.EngineEffect.Slap
import com.noikonomist.slapme.engine.EngineEffect.StartLockout
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalationTest {

    private val h = EngineHarness()

    @Test
    fun slapFiresExactlyAtTheBaseThreshold() {
        assertTrue(h.use(9.minutes + 55.seconds).ofType<Slap>().isEmpty())
        assertEquals(
            listOf(Slap(SlapReason.THRESHOLD, level = 0, packageName = FEED)),
            h.use(5.seconds).ofType<Slap>(),
        )
    }

    @Test
    fun eachSlapMovesToTheNextLevel() {
        assertEquals(listOf(0), h.use(10.minutes).ofType<Slap>().map { it.level })
        assertEquals(1, h.state.level)

        assertTrue(h.use(4.minutes + 55.seconds).ofType<Slap>().isEmpty())
        assertEquals(listOf(1), h.use(5.seconds).ofType<Slap>().map { it.level })
        assertEquals(2, h.state.level)

        val last = h.use(2.minutes)
        assertEquals(listOf(2), last.ofType<Slap>().map { it.level })
        assertEquals(1, last.ofType<StartLockout>().size)
    }

    @Test
    fun idleForTheEscalationBreakResetsTheLevel() {
        h.use(10.minutes)
        h.idle(10.minutes)
        assertEquals(0, h.state.level)
        assertTrue(h.use(9.minutes + 55.seconds).ofType<Slap>().isEmpty())
    }

    @Test
    fun shorterIdleResetsTheStreakButKeepsTheLevel() {
        h.use(10.minutes)
        h.idle(9.minutes)
        assertEquals(1, h.state.level)
        assertEquals(0.seconds, h.state.streak)
        assertEquals(listOf(1), h.use(5.minutes).ofType<Slap>().map { it.level })
    }

    @Test
    fun customLadderIsFollowed() {
        h.config = EngineConfig(levels = listOf(3.minutes, 1.minutes))

        assertEquals(1, h.use(3.minutes).ofType<Slap>().size)
        val second = h.use(1.minutes)
        assertEquals(listOf(1), second.ofType<Slap>().map { it.level })
        assertEquals("second level is the last", 1, second.ofType<StartLockout>().size)
    }

    @Test
    fun singleLevelLadderLocksOutOnTheFirstSlap() {
        h.config = EngineConfig(levels = listOf(5.minutes))
        assertEquals(1, h.use(5.minutes).ofType<StartLockout>().size)
    }

    @Test
    fun levelBeyondAShortenedLadderUsesTheLastLevel() {
        h.use(10.minutes)
        h.use(5.minutes) // level 2
        h.config = EngineConfig(levels = listOf(10.minutes, 4.minutes))

        val effects = h.use(4.minutes)
        assertEquals(1, effects.ofType<Slap>().size)
        assertEquals(1, effects.ofType<StartLockout>().size)
    }
}
