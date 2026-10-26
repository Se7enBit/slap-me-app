package com.noikonomist.slapme.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleBlockDao {
    @Query("SELECT * FROM schedule_block ORDER BY startMinute, id")
    fun observeAll(): Flow<List<ScheduleBlockEntity>>

    @Insert
    suspend fun insert(block: ScheduleBlockEntity): Long

    @Update
    suspend fun update(block: ScheduleBlockEntity)

    @Query("DELETE FROM schedule_block WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_session WHERE actualEnd IS NULL ORDER BY start DESC LIMIT 1")
    fun observeRunning(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_session WHERE id = :id")
    suspend fun get(id: Long): FocusSessionEntity?

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    /** Ends every running session at [at]; returns how many were running. */
    @Query("UPDATE focus_session SET actualEnd = :at WHERE actualEnd IS NULL")
    suspend fun endRunning(at: Long): Int

    /** Ends a session that ran to its planned end (or at [at] if it had none). */
    @Query("UPDATE focus_session SET actualEnd = COALESCE(plannedEnd, :at) WHERE id = :id AND actualEnd IS NULL")
    suspend fun endAtPlannedEnd(id: Long, at: Long)

    /** At most one session runs: starting one ends the previous one. */
    @Transaction
    suspend fun startNew(session: FocusSessionEntity): Long {
        endRunning(session.start)
        return insert(session)
    }
}

@Dao
interface SlapEventDao {
    @Insert
    suspend fun insert(event: SlapEventEntity): Long

    /** [from] inclusive, [to] exclusive. */
    @Query("SELECT * FROM slap_event WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp, id")
    fun observeBetween(from: Long, to: Long): Flow<List<SlapEventEntity>>
}

@Dao
interface OverrideEventDao {
    @Insert
    suspend fun insert(event: OverrideEventEntity): Long

    /** [from] inclusive, [to] exclusive. */
    @Query("SELECT * FROM override_event WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp, id")
    fun observeBetween(from: Long, to: Long): Flow<List<OverrideEventEntity>>
}

@Dao
interface DailyUsageDao {
    @Upsert
    suspend fun upsert(usage: DailyUsageEntity)

    /** Both ends inclusive. */
    @Query("SELECT * FROM daily_usage WHERE date >= :from AND date <= :to ORDER BY date")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<DailyUsageEntity>>
}
