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

data class DispatchExecutionUiState(
    val loading: Boolean = true, val refreshing: Boolean = false, val stale: Boolean = true, val task: Task? = null,
    val inputs: List<ActualLineInput> = emptyList(),
    val review: DispatchCommand? = null, val result: ExecutionResultState = ExecutionResultState.Idle,
    val pending: PendingCommand? = null, val error: AppError? = null,
) {
    val canRefresh: Boolean get() = !loading && result != ExecutionResultState.Sending && review == null
    val editable: Boolean get() = !loading && !stale && task?.type == TaskType.DISPATCH_GOODS && task.canExecute &&
        result != ExecutionResultState.Sending && result !is ExecutionResultState.Unresolved && pending == null && review == null
}
sealed interface DispatchExecutionIntent {
    data class QuantityChanged(val lineId: Long, val raw: String) : DispatchExecutionIntent
    data class SheetsChanged(val lineId: Long, val raw: String) : DispatchExecutionIntent
    data object ReviewClick : DispatchExecutionIntent
    data object DismissReview : DispatchExecutionIntent
    data object ConfirmClick : DispatchExecutionIntent
    data object Refresh : DispatchExecutionIntent
}
@HiltViewModel
class DispatchExecutionViewModel @Inject constructor(savedStateHandle: SavedStateHandle,
    private val loadTask: LoadTaskUseCase, private val prepare: PrepareDispatchUseCase,
    private val execute: ExecuteDispatchUseCase, pending: PendingCommandRepository,
) : ViewModel() {
    private val taskId: Long = checkNotNull(savedStateHandle["taskId"])
    private val mutableState = MutableStateFlow(DispatchExecutionUiState())
    val uiState = mutableState.asStateFlow()
    private var generation = 0L
    init {
        viewModelScope.launch { pending.observe().collect { commands ->
            val blocked = commands.firstOrNull { it.taskId == taskId && it.state !in setOf(PendingState.ACKNOWLEDGED, PendingState.REJECTED) }
            val unresolvedId = (uiState.value.result as? ExecutionResultState.Unresolved)?.command?.id ?: uiState.value.pending?.id
            val recovered = commands.firstOrNull { it.id == unresolvedId && it.state == PendingState.ACKNOWLEDGED }?.confirmation
            mutableState.update { it.copy(pending = blocked, result = recovered?.let(ExecutionResultState::Confirmed) ?: it.result) }
            if (recovered != null) refresh(resetInputs = true)
        } }
        refresh(resetInputs = true)
    }
    private fun draft(state: DispatchExecutionUiState) = DispatchDraft(state.inputs, comment = "")
    fun onIntent(intent: DispatchExecutionIntent) {
        val current = uiState.value
        when (intent) {
            is DispatchExecutionIntent.QuantityChanged -> if (current.editable) mutableState.update { it.copy(inputs = it.inputs.map { input -> if (input.taskLineId == intent.lineId) input.copy(quantity = intent.raw) else input }, error = null) }
            is DispatchExecutionIntent.SheetsChanged -> if (current.editable) mutableState.update { it.copy(inputs = it.inputs.map { input -> if (input.taskLineId == intent.lineId) input.copy(sheets = intent.raw) else input }, error = null) }
            DispatchExecutionIntent.ReviewClick -> if (current.editable) {
                when (val result = prepare(current.task!!, draft(current))) {
                    is Outcome.Success -> mutableState.update { it.copy(review = result.value, error = null) }
                    is Outcome.Failure -> mutableState.update { it.copy(error = result.error) }
                }
            }
            DispatchExecutionIntent.DismissReview -> if (current.result != ExecutionResultState.Sending) mutableState.update { it.copy(review = null) }
            DispatchExecutionIntent.ConfirmClick -> confirm()
            DispatchExecutionIntent.Refresh -> if (current.canRefresh) refresh(resetInputs = false, manual = true)
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
                        inputs = task.value.lines.map { line -> if (resetInputs) ActualLineInput(line.id) else state.inputs.firstOrNull { it.taskLineId == line.id } ?: ActualLineInput(line.id) })
                }
            }
        }
    }
}
