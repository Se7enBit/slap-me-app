package com.noikonomist.slapme.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate

@Database(
    entities = [
        ScheduleBlockEntity::class,
        FocusSessionEntity::class,
        SlapEventEntity::class,
        OverrideEventEntity::class,
        DailyUsageEntity::class,
    ],
    version = SlapMeDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SlapMeDatabase : RoomDatabase() {
    abstract fun scheduleBlockDao(): ScheduleBlockDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun slapEventDao(): SlapEventDao
    abstract fun overrideEventDao(): OverrideEventDao
    abstract fun dailyUsageDao(): DailyUsageDao

    companion object {
        /** Bump together with a migration and a new exported schema in app/schemas. */
        const val VERSION = 1

        fun create(context: Context): SlapMeDatabase =
            Room.databaseBuilder(context.applicationContext, SlapMeDatabase::class.java, "slapme.db").build()
    }
}

class Converters {
    @TypeConverter
    fun fromEpochDay(day: Long?): LocalDate? = day?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
}
