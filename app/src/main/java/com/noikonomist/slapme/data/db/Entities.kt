package com.noikonomist.slapme.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.noikonomist.slapme.engine.OverrideType
import com.noikonomist.slapme.engine.SlapReason
import java.time.LocalDate

// Times are epoch millis; enums are stored by name.

@Entity(tableName = "schedule_block")
data class ScheduleBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean,
)

/** A running session has no [actualEnd]. */
@Entity(tableName = "focus_session")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: Long,
    val plannedEnd: Long?,
    val actualEnd: Long?,
)

@Entity(tableName = "slap_event", indices = [Index("timestamp")])
data class SlapEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val packageName: String?,
    val level: Int,
    val reason: SlapReason,
)

@Entity(tableName = "override_event", indices = [Index("timestamp")])
data class OverrideEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: OverrideType,
    val durationSeconds: Long,
)

@Entity(tableName = "daily_usage")
data class DailyUsageEntity(
    @PrimaryKey val date: LocalDate,
    val countedSeconds: Long,
    val longestFocusSeconds: Long,
)
