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

sealed interface SubmissionState {
    data object Idle : SubmissionState
    data object ConfirmStart : SubmissionState
    data object Sending : SubmissionState
    data class Confirmed(val receipt: CommandConfirmation) : SubmissionState
    data class Unresolved(val command: PendingCommand, val error: AppError) : SubmissionState
    data class Rejected(val error: AppError) : SubmissionState
}
data class TaskDetailUiState(
    val loading: Boolean = true, val task: Task? = null, val error: AppError? = null,
    val submission: SubmissionState = SubmissionState.Idle, val staleTask: Boolean = false,
    val pending: PendingCommand? = null,
    val executions: List<Execution> = emptyList(), val executionsLoading: Boolean = false,
    val executionsOffset: Long = 0, val executionsMore: Boolean = false, val executionsError: AppError? = null,
    val history: List<HistoryEntry> = emptyList(), val historyLoading: Boolean = false,
    val historyOffset: Long = 0, val historyMore: Boolean = false, val historyError: AppError? = null,
)
sealed interface TaskDetailIntent {
    data object Refresh : TaskDetailIntent
    data object StartClick : TaskDetailIntent
    data object DismissConfirmation : TaskDetailIntent
    data object ConfirmStart : TaskDetailIntent
    data object LoadMoreExecutions : TaskDetailIntent
    data object LoadMoreHistory : TaskDetailIntent
}
@HiltViewModel
class TaskDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle,
    private val loadTask: LoadTaskUseCase, private val startTask: StartTaskUseCase,
    private val loadExecutions: LoadExecutionsUseCase, private val loadHistory: LoadHistoryUseCase,
    pending: PendingCommandRepository,
) : ViewModel() {
    private val taskId: Long = checkNotNull(savedStateHandle["taskId"])
    private val mutableState = MutableStateFlow(TaskDetailUiState())
    val uiState = mutableState.asStateFlow()
    private var generation = 0L
    init {
        viewModelScope.launch { pending.observe().collect { commands ->
            val current = uiState.value
            val unresolvedId = (current.submission as? SubmissionState.Unresolved)?.command?.id ?: current.pending?.id
            val recovered = commands.firstOrNull { it.id == unresolvedId && it.state == PendingState.ACKNOWLEDGED }?.confirmation
            val blocked = commands.firstOrNull { it.taskId == taskId && it.state !in setOf(PendingState.ACKNOWLEDGED, PendingState.REJECTED) }
            mutableState.update { it.copy(pending = blocked, submission = recovered?.let(SubmissionState::Confirmed) ?: it.submission) }
            if (recovered != null) refresh()
        } }
        refresh()
    }
    fun onIntent(intent: TaskDetailIntent) {
        when (intent) {
            TaskDetailIntent.Refresh -> if (uiState.value.submission != SubmissionState.Sending) refresh()
            TaskDetailIntent.StartClick -> if (uiState.value.task?.canStart == true && uiState.value.pending == null && !uiState.value.loading && !uiState.value.staleTask && uiState.value.submission !in setOf(SubmissionState.Sending, SubmissionState.ConfirmStart) && uiState.value.submission !is SubmissionState.Unresolved) mutableState.update { it.copy(submission = SubmissionState.ConfirmStart) }
            TaskDetailIntent.DismissConfirmation -> if (uiState.value.submission == SubmissionState.ConfirmStart) mutableState.update { it.copy(submission = SubmissionState.Idle) }
            TaskDetailIntent.ConfirmStart -> confirmStart()
            TaskDetailIntent.LoadMoreExecutions -> executions(reset = false)
            TaskDetailIntent.LoadMoreHistory -> history(reset = false)
        }
    }
    private fun refresh() {
        val request = ++generation
        mutableState.update { it.copy(loading = true, error = null, staleTask = true) }
        viewModelScope.launch {
            when (val result = loadTask(taskId)) {
                is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loading = false, error = result.error) }
                is Outcome.Success -> if (request == generation) {
                    mutableState.update { it.copy(loading = false, task = result.value, staleTask = false) }
                    executions(reset = true)
                    history(reset = true)
                }
            }
        }
    }
    private fun confirmStart() {
        if (uiState.value.pending != null || uiState.value.submission != SubmissionState.ConfirmStart || uiState.value.loading || uiState.value.staleTask || uiState.value.task?.canStart != true) return
        val task = uiState.value.task ?: return
        mutableState.update { it.copy(submission = SubmissionState.Sending) }
        viewModelScope.launch {
            val result = startTask(task)
            mutableState.update { it.copy(submission = when (result) {
                is CommandOutcome.Confirmed -> SubmissionState.Confirmed(result.confirmation)
                is CommandOutcome.Unresolved -> SubmissionState.Unresolved(result.command, result.error)
                is CommandOutcome.Rejected -> SubmissionState.Rejected(result.error)
            }, staleTask = result is CommandOutcome.Confirmed || result is CommandOutcome.Rejected) }
            if (result !is CommandOutcome.Unresolved) refresh()
        }
    }
    private fun executions(reset: Boolean) {
        val current = uiState.value
        if (!reset && (current.executionsLoading || !current.executionsMore)) return
        val request = generation
        mutableState.update { it.copy(executionsLoading = true, executionsError = null) }
        viewModelScope.launch {
            when (val result = loadExecutions(taskId, if (reset) 0 else current.executionsOffset)) {
                is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(executionsLoading = false, executionsError = result.error) }
                is Outcome.Success -> if (request == generation) mutableState.update { it.copy(executionsLoading = false,
                    executions = ((if (reset) emptyList() else it.executions) + result.value.items).distinctBy(Execution::id), executionsOffset = result.value.nextOffset, executionsMore = result.value.hasMore) }
            }
        }
    }
    private fun history(reset: Boolean) {
        val current = uiState.value
        if (!reset && (current.historyLoading || !current.historyMore)) return
        val request = generation
        mutableState.update { it.copy(historyLoading = true, historyError = null) }
        viewModelScope.launch {
            when (val result = loadHistory(taskId, if (reset) 0 else current.historyOffset)) {
                is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(historyLoading = false, historyError = result.error) }
                is Outcome.Success -> if (request == generation) mutableState.update { it.copy(historyLoading = false,
                    history = ((if (reset) emptyList() else it.history) + result.value.items).distinctBy(HistoryEntry::id), historyOffset = result.value.nextOffset, historyMore = result.value.hasMore) }
            }
        }
    }
}
