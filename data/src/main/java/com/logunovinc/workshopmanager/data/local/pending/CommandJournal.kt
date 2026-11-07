package com.logunovinc.workshopmanager.data.local.pending

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

sealed interface PrepareResult {
    data class Created(val command: PendingCommandEntity) : PrepareResult
    data class Existing(val command: PendingCommandEntity) : PrepareResult
}
interface CommandJournal {
    suspend fun prepare(command: PendingCommandEntity): PrepareResult
    suspend fun get(id: String): PendingCommandEntity?
    fun observe(baseUrl: String, actorId: Long): Flow<List<PendingCommandEntity>>
    suspend fun update(command: PendingCommandEntity, releaseSlot: Boolean = false)
}
class RoomCommandJournal(private val database: CommandDatabase) : CommandJournal {
    private val dao get() = database.commands()
    override suspend fun prepare(command: PendingCommandEntity): PrepareResult = database.withTransaction {
        val existing = dao.byFingerprint(command.baseUrl, command.actorId, command.taskId, command.fingerprint, command.scope)
            ?: dao.active(command.baseUrl, command.actorId, command.taskId, command.scope)
        if (existing != null) PrepareResult.Existing(existing)
        else {
            dao.insert(command)
            dao.insertSlot(ActiveCommandEntity(command.baseUrl, command.actorId, command.taskId, command.id, command.scope))
            PrepareResult.Created(command)
        }
    }
    override suspend fun get(id: String) = dao.get(id)
    override fun observe(baseUrl: String, actorId: Long) = dao.observe(baseUrl, actorId)
    override suspend fun update(command: PendingCommandEntity, releaseSlot: Boolean) = database.withTransaction {
        dao.update(command)
        if (releaseSlot) dao.release(command.id)
    }
}
