package com.logunovinc.workshopmanager.feature.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.pending.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TaskDetailUiState(
    val loading: Boolean = true, val task: Task? = null, val error: AppError? = null,
    val lastConfirmation: CommandConfirmation? = null, val staleTask: Boolean = false,
    val pending: PendingCommand? = null,
    val executions: List<Execution> = emptyList(), val executionsLoading: Boolean = false,
    val executionsOffset: Long = 0, val executionsMore: Boolean = false, val executionsError: AppError? = null,
    val debugHistoryAvailable: Boolean = false, val debugHistoryExpanded: Boolean = false,
    val history: List<HistoryEntry> = emptyList(), val historyLoading: Boolean = false,
    val historyOffset: Long = 0, val historyMore: Boolean = false, val historyError: AppError? = null,
) {
    val executionAvailable: Boolean get() = !loading && !staleTask && pending == null && task?.canExecute == true
}
sealed interface TaskDetailIntent {
    data object Refresh : TaskDetailIntent
    data object LoadMoreExecutions : TaskDetailIntent
    data object ToggleDebugHistory : TaskDetailIntent
    data object LoadMoreHistory : TaskDetailIntent
}
@HiltViewModel
class TaskDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle,
    private val loadTask: LoadTaskUseCase,
    private val loadExecutions: LoadExecutionsUseCase, private val loadHistory: LoadHistoryUseCase,
    pending: PendingCommandRepository,
    debugOptions: TaskDebugOptions = TaskDebugOptions(),
) : ViewModel() {
    private val taskId: Long = checkNotNull(savedStateHandle["taskId"])
    private val mutableState = MutableStateFlow(TaskDetailUiState(debugHistoryAvailable = debugOptions.showHistoryAvailable))
    val uiState = mutableState.asStateFlow()
    private var generation = 0L
    private var historyGeneration = 0L
    private var historyJob: Job? = null
    init {
        viewModelScope.launch { pending.observe().collect { commands ->
            val current = uiState.value
            val unresolvedId = current.pending?.id
            val recovered = commands.firstOrNull { it.id == unresolvedId && it.state == PendingState.ACKNOWLEDGED }?.confirmation
            val blocked = commands.firstOrNull { it.taskId == taskId && it.state !in setOf(PendingState.ACKNOWLEDGED, PendingState.REJECTED) }
            mutableState.update { it.copy(pending = blocked, lastConfirmation = recovered ?: it.lastConfirmation) }
            if (recovered != null) refresh()
        } }
        refresh()
    }
    fun onIntent(intent: TaskDetailIntent) {
        when (intent) {
            TaskDetailIntent.Refresh -> refresh()
            TaskDetailIntent.LoadMoreExecutions -> executions(reset = false)
            TaskDetailIntent.ToggleDebugHistory -> toggleDebugHistory()
            TaskDetailIntent.LoadMoreHistory -> history(reset = false)
        }
    }
    private fun refresh() {
        val request = ++generation
        historyGeneration++
        historyJob?.cancel()
        mutableState.update { it.copy(loading = true, error = null, staleTask = true,
            history = emptyList(), historyLoading = false, historyOffset = 0, historyMore = false, historyError = null) }
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
    private fun toggleDebugHistory() {
        val current = uiState.value
        if (!current.debugHistoryAvailable) return
        if (current.debugHistoryExpanded) {
            historyGeneration++
            historyJob?.cancel()
            mutableState.update { it.copy(debugHistoryExpanded = false, history = emptyList(), historyLoading = false,
                historyOffset = 0, historyMore = false, historyError = null) }
        } else {
            mutableState.update { it.copy(debugHistoryExpanded = true) }
            history(reset = true)
        }
    }
    private fun history(reset: Boolean) {
        val current = uiState.value
        if (!current.debugHistoryAvailable || !current.debugHistoryExpanded || current.loading || current.staleTask || current.task == null) return
        if (!reset && (current.historyLoading || !current.historyMore)) return
        if (reset) { historyGeneration++; historyJob?.cancel() }
        val request = generation
        val auditRequest = historyGeneration
        mutableState.update { it.copy(historyLoading = true, historyError = null) }
        historyJob = viewModelScope.launch {
            val result = loadHistory(taskId, if (reset) 0 else current.historyOffset)
            if (request != generation || auditRequest != historyGeneration || !uiState.value.debugHistoryExpanded) return@launch
            when (result) {
                is Outcome.Failure -> mutableState.update { it.copy(historyLoading = false, historyError = result.error) }
                is Outcome.Success -> mutableState.update { it.copy(historyLoading = false,
                    history = ((if (reset) emptyList() else it.history) + result.value.items).distinctBy(HistoryEntry::id), historyOffset = result.value.nextOffset, historyMore = result.value.hasMore) }
            }
        }
    }
}
