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
    override suspend fun receive(task: Task, command: ReceiveCommand): CommandOutcome {
        if (task.type != TaskType.RECEIVE_GOODS || !task.canExecute || task.transferVersion == null)
            return CommandOutcome.Rejected(null, AppError(ErrorKind.FORBIDDEN, "Приёмка недоступна; обновите задание"))
        val transferVersion = task.transferVersion ?: return CommandOutcome.Rejected(null, AppError(ErrorKind.VALIDATION, "Неизвестна версия перемещения"))
        val byId = task.lines.associateBy(TaskLine::id)
        val lines = command.lines.map { line ->
            val product = byId[line.taskLineId]
            "${product?.productName ?: "Строка"} · строка №${line.taskLineId}: " + (line.sheets?.let { "$it листов" } ?: "${line.quantity?.toPlainString()} ${product?.baseUnit.orEmpty()}")
        }
        val mode = if (command.final) "Окончательная приёмка" else "Частичная приёмка — продолжить ожидание"
        val summary = CommandSummary("$mode · задание №${task.id}", lines + listOf("Версия задания ${task.version}, перемещения ${task.transferVersion}") +
            (if (command.reason.isNotBlank()) listOf("Причина: ${command.reason}") else emptyList()), command.comment)
        return dispatcher.submit(task.id, "RECEIVE_GOODS", "POST", "api/workshop/tasks/${task.id}/executions", summary) { token ->
            json.encodeToString(ReceiveRequestDto(expectedVersion = task.version, expectedTransferVersion = transferVersion,
                clientToken = token, comment = command.comment, lines = command.lines.map { ReceiveLineDto(it.taskLineId, it.quantity, it.sheets) },
                final = command.final, reason = command.reason)).toByteArray(Charsets.UTF_8)
        }
    }
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
