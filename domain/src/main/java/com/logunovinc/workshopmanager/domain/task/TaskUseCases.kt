package com.logunovinc.workshopmanager.domain.task

import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.pending.CommandOutcome

/** Reads one server-filtered page without pretending local filtering covers the whole scope. */
class LoadTasksUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(warehouseId: Long, filter: TaskFilter, offset: Long = 0): Outcome<Page<Task>> =
        if (warehouseId <= 0 || offset < 0 || filter.type == TaskType.UNKNOWN) Outcome.Failure(AppError(ErrorKind.VALIDATION, "Некорректный фильтр заданий"))
        else repository.tasks(warehouseId, filter, 50, offset)
}
/** Reads fresh server versions for the navigation argument. */
class LoadTaskUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(taskId: Long): Outcome<Task> = if (taskId > 0) repository.task(taskId)
        else Outcome.Failure(AppError(ErrorKind.VALIDATION, "Некорректный номер задания"))
}
/** The persisted start request is emitted only after explicit user confirmation. */
class StartTaskUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task): CommandOutcome = if (task.canStart) repository.start(task)
        else CommandOutcome.Rejected(null, AppError(ErrorKind.FORBIDDEN, "Сейчас нельзя взять это задание в работу"))
}
/** Loads full execution history through its dedicated paginated endpoint. */
class LoadExecutionsUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(taskId: Long, offset: Long = 0): Outcome<Page<Execution>> =
        if (taskId <= 0 || offset < 0) Outcome.Failure(AppError(ErrorKind.VALIDATION, "Некорректная страница фактов")) else repository.executions(taskId, 50, offset)
}
/** Loads audit pages separately from the latest fifty entries embedded in a task. */
class LoadHistoryUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(taskId: Long, offset: Long = 0): Outcome<Page<HistoryEntry>> =
        if (taskId <= 0 || offset < 0) Outcome.Failure(AppError(ErrorKind.VALIDATION, "Некорректная страница истории")) else repository.history(taskId, 50, offset)
}
