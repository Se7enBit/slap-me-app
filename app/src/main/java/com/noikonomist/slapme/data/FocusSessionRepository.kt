package com.noikonomist.slapme.data

import com.noikonomist.slapme.data.db.FocusSessionDao
import com.noikonomist.slapme.data.db.FocusSessionEntity
import com.noikonomist.slapme.engine.FocusSession
import java.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FocusSessionRepository(private val dao: FocusSessionDao, private val clock: Clock) {

    /** The session that hasn't ended, if any. It may be past its planned end until the engine reports it. */
    val running: Flow<FocusSession?> = dao.observeRunning()
        .map { it?.let { e -> FocusSession(e.id, e.start, e.plannedEnd) } }
        .distinctUntilChanged()

    /** Starts a session now, for [duration] or until stopped. A running session is ended first. */
    suspend fun start(duration: Duration?): FocusSession {
        val now = clock.millis()
        val plannedEnd = duration?.let { now + it.inWholeMilliseconds }
        val id = dao.startNew(FocusSessionEntity(start = now, plannedEnd = plannedEnd, actualEnd = null))
        return FocusSession(id, now, plannedEnd)
    }

    /** Ends the running session now. Returns false if none was running. */
    suspend fun stop(): Boolean = dao.endRunning(clock.millis()) > 0

    /** The engine reported that a planned session reached its end. */
    suspend fun markExpired(id: Long) = dao.endAtPlannedEnd(id, clock.millis())
}
