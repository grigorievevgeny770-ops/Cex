package com.logunovinc.workshopmanager.domain.task

import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.pending.CommandOutcome

/** Exact reason validation uses Unicode code points, matching the server string length limit. */
object CloseIncompleteReason {
    fun validate(raw: String): Outcome<String> {
        val reason = raw.trim()
        return if (reason.isBlank() || reason.codePointCount(0, reason.length) > 2000)
            Outcome.Failure(AppError(ErrorKind.VALIDATION, "Укажите причину закрытия: от 1 до 2000 символов"))
        else Outcome.Success(reason)
    }
}
/** Checks only the selected server's close route, lazily before showing the confirmation. */
class CheckCloseIncompleteCapabilityUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(): Outcome<Boolean> = when (val result = repository.closeIncompleteCapability()) {
        is Outcome.Failure -> result
        is Outcome.Success -> if (result.value) result else Outcome.Failure(AppError(ErrorKind.INCOMPATIBLE_SERVER, "Сервер ещё не поддерживает закрытие остатка задания"))
    }
}
/** Closes remaining work without inventing an execution, stock movement or loss approval. */
class CloseIncompleteTaskUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task, rawReason: String): CommandOutcome {
        if (!task.canCloseIncomplete) return CommandOutcome.Rejected(null, AppError(ErrorKind.FORBIDDEN, "Закрытие остатка недоступно; обновите карточку"))
        return when (val reason = CloseIncompleteReason.validate(rawReason)) {
            is Outcome.Failure -> CommandOutcome.Rejected(null, reason.error)
            is Outcome.Success -> repository.closeIncomplete(task, reason.value)
        }
    }
}
