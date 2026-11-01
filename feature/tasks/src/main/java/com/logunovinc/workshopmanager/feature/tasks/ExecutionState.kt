package com.logunovinc.workshopmanager.feature.tasks

import com.logunovinc.workshopmanager.domain.AppError
import com.logunovinc.workshopmanager.domain.pending.*

sealed interface ExecutionResultState {
    data object Idle : ExecutionResultState
    data object Sending : ExecutionResultState
    data class Confirmed(val receipt: CommandConfirmation) : ExecutionResultState
    data class Unresolved(val command: PendingCommand, val error: AppError) : ExecutionResultState
    data class Rejected(val error: AppError) : ExecutionResultState
}
fun CommandOutcome.executionState(): ExecutionResultState = when (this) {
    is CommandOutcome.Confirmed -> ExecutionResultState.Confirmed(confirmation)
    is CommandOutcome.Unresolved -> ExecutionResultState.Unresolved(command, error)
    is CommandOutcome.Rejected -> ExecutionResultState.Rejected(error)
}
