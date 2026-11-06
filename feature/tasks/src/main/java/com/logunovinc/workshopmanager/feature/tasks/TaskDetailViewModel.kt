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

data class CloseIncompleteDialog(val task: Task, val reason: String = "")

data class TaskDetailUiState(
    val loading: Boolean = true, val refreshing: Boolean = false, val task: Task? = null, val error: AppError? = null,
    val lastConfirmation: CommandConfirmation? = null, val staleTask: Boolean = false,
    val pending: PendingCommand? = null, val retrying: Boolean = false, val retryError: AppError? = null,
    val closeDialog: CloseIncompleteDialog? = null, val closeChecking: Boolean = false,
    val closeSending: Boolean = false, val closeError: AppError? = null,
    val executions: List<Execution> = emptyList(), val executionsLoading: Boolean = false,
    val executionsOffset: Long = 0, val executionsMore: Boolean = false, val executionsError: AppError? = null,
    val debugHistoryAvailable: Boolean = false, val debugHistoryExpanded: Boolean = false,
    val history: List<HistoryEntry> = emptyList(), val historyLoading: Boolean = false,
    val historyOffset: Long = 0, val historyMore: Boolean = false, val historyError: AppError? = null,
) {
    val canRefresh: Boolean get() = !loading && !closeSending && !retrying && !closeChecking && closeDialog == null
    val executionAvailable: Boolean get() = !loading && !staleTask && !retrying && pending == null && !closeSending && !closeChecking && closeDialog == null && task?.canExecute == true
    val closeAvailable: Boolean get() = !loading && !staleTask && !retrying && pending == null && !closeSending && !closeChecking && closeDialog == null && task?.canCloseIncomplete == true
}
sealed interface TaskDetailIntent {
    data object CloseIncompleteClick : TaskDetailIntent
    data class CloseReasonChanged(val value: String) : TaskDetailIntent
    data object DismissClose : TaskDetailIntent
    data object ConfirmClose : TaskDetailIntent
    data object RetryPendingClick : TaskDetailIntent
    data object PullRefresh : TaskDetailIntent
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
    private val checkCloseCapability: CheckCloseIncompleteCapabilityUseCase,
    private val closeIncomplete: CloseIncompleteTaskUseCase,
    private val retryPending: RetryPendingCommandUseCase,
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
            mutableState.update { it.copy(pending = blocked, lastConfirmation = recovered ?: it.lastConfirmation, closeError = if (recovered != null) null else it.closeError, retryError = if (recovered != null) null else it.retryError) }
            if (recovered != null) refresh()
        } }
        refresh()
    }
    fun onIntent(intent: TaskDetailIntent) {
        when (intent) {
            TaskDetailIntent.RetryPendingClick -> retrySaved()
            TaskDetailIntent.CloseIncompleteClick -> openClose()
            is TaskDetailIntent.CloseReasonChanged -> if (!uiState.value.closeSending) mutableState.update { it.copy(closeDialog = it.closeDialog?.copy(reason = intent.value), closeError = null) }
            TaskDetailIntent.DismissClose -> if (!uiState.value.closeSending) mutableState.update { it.copy(closeDialog = null, closeError = null) }
            TaskDetailIntent.ConfirmClose -> confirmClose()
            TaskDetailIntent.Refresh -> if (uiState.value.canRefresh) refresh()
            TaskDetailIntent.PullRefresh -> if (uiState.value.canRefresh) refresh(manual = true)
            TaskDetailIntent.LoadMoreExecutions -> executions(reset = false)
            TaskDetailIntent.ToggleDebugHistory -> toggleDebugHistory()
            TaskDetailIntent.LoadMoreHistory -> history(reset = false)
        }
    }
    private fun retrySaved() {
        val state = uiState.value
        val pending = state.pending ?: return
        if (state.retrying || state.closeSending) return
        mutableState.update { it.copy(retrying = true, retryError = null) }
        viewModelScope.launch {
            try {
                when (val result = retryPending(pending.id)) {
                    is CommandOutcome.Confirmed -> {
                        val alreadyRead = uiState.value.lastConfirmation?.commandId == result.confirmation.commandId
                        mutableState.update { it.copy(lastConfirmation = result.confirmation, pending = if (it.pending?.id == pending.id) null else it.pending, retryError = null) }
                        if (!alreadyRead) refresh()
                    }
                    is CommandOutcome.Unresolved -> mutableState.update { it.copy(pending = result.command, retryError = result.error) }
                    is CommandOutcome.Rejected -> {
                        mutableState.update { it.copy(retryError = result.error) }
                        refresh()
                    }
                }
            } finally { mutableState.update { it.copy(retrying = false) } }
        }
    }
    private fun openClose() {
        val state = uiState.value
        if (!state.closeAvailable) return
        val task = state.task ?: return
        val request = generation
        mutableState.update { it.copy(closeChecking = true, closeError = null) }
        viewModelScope.launch {
            val result = checkCloseCapability()
            if (request != generation) return@launch
            when(result) {
                is Outcome.Failure -> mutableState.update { it.copy(closeChecking = false, closeError = result.error) }
                is Outcome.Success -> mutableState.update { it.copy(closeChecking = false,
                    closeDialog = if (!it.loading && !it.staleTask && it.pending == null && it.task == task && task.canCloseIncomplete) CloseIncompleteDialog(task) else null) }
            }
        }
    }
    private fun confirmClose() {
        val current = uiState.value
        val dialog = current.closeDialog ?: return
        if (current.closeSending || current.loading || current.staleTask || current.pending != null || current.task != dialog.task || !dialog.task.canCloseIncomplete) return
        val reason = when (val result = CloseIncompleteReason.validate(dialog.reason)) {
            is Outcome.Failure -> { mutableState.update { it.copy(closeError = result.error) };return }
            is Outcome.Success -> result.value
        }
        mutableState.update { it.copy(closeSending = true, closeError = null, closeDialog = dialog.copy(reason = reason)) }
        viewModelScope.launch {
            when(val result = closeIncomplete(dialog.task, reason)) {
                is CommandOutcome.Confirmed -> {
                    mutableState.update { it.copy(closeSending = false, closeDialog = null, lastConfirmation = result.confirmation) }
                    refresh()
                }
                is CommandOutcome.Unresolved -> mutableState.update { it.copy(closeSending = false, closeDialog = null, pending = result.command, closeError = result.error) }
                is CommandOutcome.Rejected -> {
                    mutableState.update { it.copy(closeSending = false, closeDialog = null, closeError = result.error) }
                    // A rejected version/right check is not permission to rewrite the pending payload.
                    refresh()
                }
            }
        }
    }
    private fun refresh(manual: Boolean = false) {
        if (uiState.value.closeSending) return
        val request = ++generation
        historyGeneration++
        historyJob?.cancel()
        mutableState.update { it.copy(loading = true, refreshing = manual, error = null, staleTask = true, closeChecking = false, closeDialog = null,
            history = emptyList(), historyLoading = false, historyOffset = 0, historyMore = false, historyError = null) }
        viewModelScope.launch {
            when (val result = loadTask(taskId)) {
                is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loading = false, refreshing = false, error = result.error) }
                is Outcome.Success -> if (request == generation) {
                    mutableState.update { it.copy(loading = false, refreshing = false, task = result.value, staleTask = false) }
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
