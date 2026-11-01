package com.logunovinc.workshopmanager.domain.task

import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.pending.CommandOutcome

data class DispatchDraft(val lines: List<ActualLineInput>, val comment: String = "")
data class DispatchCommand(val lines: List<ActualLine>, val comment: String)
object DispatchValidator {
    fun validate(task: Task, draft: DispatchDraft): Outcome<DispatchCommand> {
        if (task.type != TaskType.DISPATCH_GOODS || !task.canExecute) return Outcome.Failure(AppError(ErrorKind.FORBIDDEN, "Отправка этого задания сейчас недоступна"))
        if (task.version <= 0) return Outcome.Failure(AppError(ErrorKind.VALIDATION, "Обновите версию задания"))
        if (draft.comment.length > 2000) return Outcome.Failure(AppError(ErrorKind.VALIDATION, "Комментарий должен быть не длиннее 2000 символов"))
        return when (val actual = ActualLinesValidator.validate(task, draft.lines)) {
            is Outcome.Failure -> actual
            is Outcome.Success -> if (actual.value.isEmpty()) Outcome.Failure(AppError(ErrorKind.VALIDATION, "Укажите фактически отданное положительное количество"))
                else Outcome.Success(DispatchCommand(actual.value, draft.comment))
        }
    }
}
/** Prepares the exact physical step for an explicit confirmation without creating stock movements. */
class PrepareDispatchUseCase {
    operator fun invoke(task: Task, draft: DispatchDraft): Outcome<DispatchCommand> = DispatchValidator.validate(task, draft)
}
/** Executes a reviewed shipment; recipient credit remains exclusively server-side. */
class ExecuteDispatchUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task, draft: DispatchDraft): CommandOutcome = when (val prepared = DispatchValidator.validate(task, draft)) {
        is Outcome.Failure -> CommandOutcome.Rejected(null, prepared.error)
        is Outcome.Success -> repository.dispatch(task, prepared.value)
    }
}
