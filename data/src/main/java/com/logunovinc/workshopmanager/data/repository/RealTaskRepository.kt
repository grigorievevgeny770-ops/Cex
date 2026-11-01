package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.data.remote.dto.*
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.production.*
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
    private val productionMapper = ProductionMapper()
    override suspend fun manufacturingPreview(task: Task, command: ManufacturingCommand): Outcome<ManufacturingPreview> = when (command) {
        is ManufacturingCommand.ByRecipe -> map(remote.manufacturingPreview(task.id, ByRecipePreviewDto(taskVersion = command.taskVersion, recipeVersion = command.recipeVersion, output = productionMapper.output(command.output))), productionMapper::preview)
        is ManufacturingCommand.Prototype -> map(remote.manufacturingPreview(task.id, PrototypePreviewDto(taskVersion = command.taskVersion, recipeVersion = command.recipeVersion, output = productionMapper.output(command.output), materials = productionMapper.materials(command))), productionMapper::preview)
    }
    override suspend fun manufacture(task: Task, command: ManufacturingCommand, preview: ManufacturingPreview, comment: String): CommandOutcome {
        if (task.type != TaskType.MANUFACTURE_PRODUCT || !task.canExecute || !ManufacturingValidator.previewMatches(task, command, preview) || !preview.canExecute)
            return CommandOutcome.Rejected(null, AppError(ErrorKind.CONFLICT, "Нужен актуальный расчёт производства v2"))
        val materials = preview.materials.map { "Будет списано ${it.productName}: ${it.sheets?.let { count -> "$count листов" } ?: "${it.quantity.toPlainString()} ${it.baseUnit}"}" }
        val summary = CommandSummary("${if (command is ManufacturingCommand.Prototype) "Первый прототип" else "Выпуск по норме"} · задание №${task.id}",
            listOf("Будет изготовлено ${preview.output.productName}: ${preview.output.sheets?.let { "$it листов" } ?: "${preview.output.quantity.toPlainString()} ${preview.output.baseUnit}"}",
                "Версия задания ${command.taskVersion}, нормы ${command.recipeVersion}") + materials, comment)
        return dispatcher.submit(task.id, command.mode.name.lowercase(), "POST", "api/workshop/v2/tasks/${task.id}/manufacturing-executions", summary) { token ->
            when (command) {
                is ManufacturingCommand.ByRecipe -> json.encodeToString(ByRecipeExecutionDto(taskVersion = command.taskVersion, recipeVersion = command.recipeVersion,
                    clientToken = token, output = productionMapper.output(command.output), comment = comment))
                is ManufacturingCommand.Prototype -> json.encodeToString(PrototypeExecutionDto(taskVersion = command.taskVersion, recipeVersion = command.recipeVersion,
                    clientToken = token, output = productionMapper.output(command.output), materials = productionMapper.materials(command), comment = comment))
            }.toByteArray(Charsets.UTF_8)
        }
    }
    override suspend fun dispatch(task: Task, command: DispatchCommand): CommandOutcome {
        if (task.type != TaskType.DISPATCH_GOODS || !task.canExecute)
            return CommandOutcome.Rejected(null, AppError(ErrorKind.FORBIDDEN, "Отправка недоступна; обновите задание"))
        val byId = task.lines.associateBy(TaskLine::id)
        val summaryLines = command.lines.map { actual ->
            val line = byId[actual.taskLineId]
            "${line?.productName ?: "Строка"} · строка №${actual.taskLineId}: " + (actual.sheets?.let { "$it листов" } ?: "${actual.quantity?.toPlainString()} ${line?.baseUnit.orEmpty()}")
        }
        val summary = CommandSummary("Отправка · задание №${task.id}", summaryLines + listOf("Версия задания ${task.version}", "В пути, получатель ещё не принял"), command.comment)
        return dispatcher.submit(task.id, "DISPATCH_GOODS", "POST", "api/workshop/tasks/${task.id}/executions", summary) { token ->
            json.encodeToString(DispatchRequestDto(expectedVersion = task.version, clientToken = token, comment = command.comment,
                lines = command.lines.map { DispatchLineDto(it.taskLineId, it.quantity, it.sheets) })).toByteArray(Charsets.UTF_8)
        }
    }
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
    override suspend fun executions(taskId: Long, limit: Int, offset: Long) = map(remote.executions(taskId, limit, offset)) { mapper.page(it, mapper::execution) }
    override suspend fun execution(taskId: Long, executionId: Long) = map(remote.execution(taskId, executionId), mapper::execution)
    override suspend fun history(taskId: Long, limit: Int, offset: Long) = map(remote.history(taskId, limit, offset)) { mapper.page(it, mapper::history) }
}
