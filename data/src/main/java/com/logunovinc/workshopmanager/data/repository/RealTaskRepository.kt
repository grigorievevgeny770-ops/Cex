package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.data.remote.dto.*
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.pending.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RealTaskRepository(private val remote: WorkshopRemoteDataSource, private val mapper: TaskMapper,
    private val dispatcher: CommandDispatcher, private val json: Json, private val errors: ApiErrorMapper) : TaskRepository {
    private fun <A, B> map(result: Outcome<A>, block: (A) -> B): Outcome<B> = when (result) {
        is Outcome.Failure -> result
        is Outcome.Success -> try { Outcome.Success(block(result.value)) } catch (error: Exception) { Outcome.Failure(errors.throwable(error)) }
    }
    override suspend fun tasks(warehouseId: Long, filter: TaskFilter, limit: Int, offset: Long) = map(remote.tasks(warehouseId, filter, limit, offset)) { mapper.page(it, mapper::task) }
    override suspend fun task(id: Long) = map(remote.task(id), mapper::task)
    override suspend fun start(task: Task): CommandOutcome {
        if (!task.canStart) return CommandOutcome.Rejected(null, AppError(ErrorKind.FORBIDDEN, "Действие недоступно"))
        return dispatcher.submit(task.id, "start", "POST", "api/workshop/tasks/${task.id}/start",
            CommandSummary("Взять в работу задание №${task.id}", listOf("Версия задания: ${task.version}"))) { token ->
            json.encodeToString(StartTaskRequestDto(task.version, token)).toByteArray(Charsets.UTF_8)
        }
    }
    override suspend fun executions(taskId: Long, limit: Int, offset: Long) = map(remote.executions(taskId, limit, offset)) { mapper.page(it, mapper::execution) }
    override suspend fun execution(taskId: Long, executionId: Long) = map(remote.execution(taskId, executionId), mapper::execution)
    override suspend fun history(taskId: Long, limit: Int, offset: Long) = map(remote.history(taskId, limit, offset)) { mapper.page(it, mapper::history) }
}
