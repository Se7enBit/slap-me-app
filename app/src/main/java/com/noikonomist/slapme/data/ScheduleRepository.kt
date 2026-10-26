package com.noikonomist.slapme.data

import com.noikonomist.slapme.data.db.ScheduleBlockDao
import com.noikonomist.slapme.data.db.ScheduleBlockEntity
import com.noikonomist.slapme.engine.ScheduleBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScheduleRepository(private val dao: ScheduleBlockDao) {

    val blocks: Flow<List<ScheduleBlock>> = dao.observeAll().map { rows -> rows.map { it.toBlock() } }

    /** Stores a new block (its id is ignored) and returns the new id. */
    suspend fun add(block: ScheduleBlock): Long = dao.insert(block.toEntity().copy(id = 0))

    suspend fun update(block: ScheduleBlock) = dao.update(block.toEntity())

    suspend fun delete(id: Long) = dao.delete(id)

    private fun ScheduleBlockEntity.toBlock() = ScheduleBlock(id, daysMask, startMinute, endMinute, enabled)

    private fun ScheduleBlock.toEntity() = ScheduleBlockEntity(id, daysMask, startMinute, endMinute, enabled)
}
