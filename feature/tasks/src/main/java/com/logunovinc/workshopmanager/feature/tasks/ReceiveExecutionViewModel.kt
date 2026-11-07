package com.logunovinc.workshopmanager.feature.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.pending.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ReceiveExecutionUiState(
    val loading: Boolean = true, val refreshing: Boolean = false, val stale: Boolean = true, val task: Task? = null,
    val inputs: List<ActualLineInput> = emptyList(), val final: Boolean = false, val reason: String = "",
    val review: ReceiveCommand? = null, val result: ExecutionResultState = ExecutionResultState.Idle,
    val pending: PendingCommand? = null, val error: AppError? = null,
) {
    val canRefresh: Boolean get() = !loading && result != ExecutionResultState.Sending && review == null
    val editable: Boolean get() = !loading && !stale && task?.type == TaskType.RECEIVE_GOODS && task.canExecute &&
        result != ExecutionResultState.Sending && result !is ExecutionResultState.Unresolved && pending == null && review == null
}
sealed interface ReceiveExecutionIntent {
    data class QuantityChanged(val lineId: Long, val raw: String) : ReceiveExecutionIntent
    data class SheetsChanged(val lineId: Long, val raw: String) : ReceiveExecutionIntent
    data class FinalChanged(val value: Boolean) : ReceiveExecutionIntent
    data class ReasonChanged(val value: String) : ReceiveExecutionIntent
    data object ReviewClick : ReceiveExecutionIntent
    data object DismissReview : ReceiveExecutionIntent
    data object ConfirmClick : ReceiveExecutionIntent
    data object PullRefresh : ReceiveExecutionIntent
    data object Refresh : ReceiveExecutionIntent
}
@HiltViewModel
class ReceiveExecutionViewModel @Inject constructor(savedStateHandle: SavedStateHandle,
    private val loadTask: LoadTaskUseCase, private val prepare: PrepareReceiveUseCase,
    private val execute: ExecuteReceiveUseCase, pending: PendingCommandRepository,
) : ViewModel() {
    private val taskId: Long = checkNotNull(savedStateHandle["taskId"])
    private val mutableState = MutableStateFlow(ReceiveExecutionUiState(final = savedStateHandle.get<Boolean>("final") ?: false))
    val uiState = mutableState.asStateFlow()
    private var generation = 0L
    init {
        viewModelScope.launch { pending.observe().collect { commands ->
            val blocked = commands.firstOrNull { it.taskId == taskId && !it.action.startsWith("REQUEST_") && !it.action.startsWith("SHEET_") && it.state !in setOf(PendingState.ACKNOWLEDGED, PendingState.REJECTED) }
            val unresolvedId = (uiState.value.result as? ExecutionResultState.Unresolved)?.command?.id ?: uiState.value.pending?.id
            val recovered = commands.firstOrNull { it.id == unresolvedId && it.state == PendingState.ACKNOWLEDGED }?.confirmation
            mutableState.update { it.copy(pending = blocked, result = recovered?.let(ExecutionResultState::Confirmed) ?: it.result) }
            if (recovered != null) refresh(resetInputs = true)
        } }
        refresh(resetInputs = true)
    }
    private fun draft(state: ReceiveExecutionUiState) = ReceiveDraft(state.inputs, state.final, state.reason, comment = "")
    fun onIntent(intent: ReceiveExecutionIntent) {
        val current = uiState.value
        when (intent) {
            is ReceiveExecutionIntent.QuantityChanged -> if (current.editable) mutableState.update { it.copy(inputs = it.inputs.map { input -> if (input.taskLineId == intent.lineId) input.copy(quantity = intent.raw) else input }, error = null) }
            is ReceiveExecutionIntent.SheetsChanged -> if (current.editable) mutableState.update { it.copy(inputs = it.inputs.map { input -> if (input.taskLineId == intent.lineId) input.copy(sheets = intent.raw) else input }, error = null) }
            is ReceiveExecutionIntent.FinalChanged -> if (current.editable) mutableState.update { it.copy(final = intent.value, error = null) }
            is ReceiveExecutionIntent.ReasonChanged -> if (current.editable) mutableState.update { it.copy(reason = intent.value, error = null) }
            ReceiveExecutionIntent.ReviewClick -> if (current.editable) {
                when (val result = prepare(current.task!!, draft(current))) {
                    is Outcome.Success -> mutableState.update { it.copy(review = result.value, error = null) }
                    is Outcome.Failure -> mutableState.update { it.copy(error = result.error) }
                }
            }
            ReceiveExecutionIntent.DismissReview -> if (current.result != ExecutionResultState.Sending) mutableState.update { it.copy(review = null) }
            ReceiveExecutionIntent.ConfirmClick -> confirm()
            ReceiveExecutionIntent.Refresh -> if (current.canRefresh) refresh(resetInputs = false)
            ReceiveExecutionIntent.PullRefresh -> if (current.canRefresh) refresh(resetInputs = false, manual = true)
        }
    }
    private fun confirm() {
        val state = uiState.value
        val task = state.task ?: return
        if (state.review == null || state.loading || state.stale || state.result == ExecutionResultState.Sending || state.pending != null) return
        val frozenDraft = draft(state)
        mutableState.update { it.copy(result = ExecutionResultState.Sending, error = null) }
        viewModelScope.launch {
            val result = execute(task, frozenDraft)
            mutableState.update { it.copy(review = null, result = result.executionState(), stale = result !is CommandOutcome.Unresolved) }
            when (result) {
                is CommandOutcome.Confirmed -> refresh(resetInputs = true)
                is CommandOutcome.Rejected -> refresh(resetInputs = false)
                is CommandOutcome.Unresolved -> Unit
            }
        }
    }
    private fun refresh(resetInputs: Boolean, manual: Boolean = false) {
        val request = ++generation
        mutableState.update { it.copy(loading = true, refreshing = manual, stale = true, review = null, error = null) }
        viewModelScope.launch {
            when (val task = loadTask(taskId)) {
                is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loading = false, refreshing = false, error = task.error) }
                is Outcome.Success -> if (request == generation) mutableState.update { state ->
                    state.copy(loading = false, refreshing = false, stale = false, task = task.value,
                        inputs = task.value.lines.map { line -> if (resetInputs) ActualLineInput(line.id) else state.inputs.firstOrNull { it.taskLineId == line.id } ?: ActualLineInput(line.id) },
                        final = if (resetInputs && state.result is ExecutionResultState.Confirmed) false else state.final, reason = if (resetInputs) "" else state.reason)
                }
            }
        }
    }
}
