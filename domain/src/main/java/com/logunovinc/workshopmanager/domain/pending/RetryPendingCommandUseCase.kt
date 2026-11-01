package com.logunovinc.workshopmanager.domain.pending

import com.logunovinc.workshopmanager.domain.*

/** Explicitly retries an already confirmed user intent, preserving its original identity and body. */
class RetryPendingCommandUseCase(private val repository: PendingCommandRepository) {
    suspend operator fun invoke(commandId: String): CommandOutcome =
        if (commandId.isBlank()) CommandOutcome.Rejected(null, AppError(ErrorKind.VALIDATION, "Не выбрана команда для повтора"))
        else repository.retry(commandId)
}
