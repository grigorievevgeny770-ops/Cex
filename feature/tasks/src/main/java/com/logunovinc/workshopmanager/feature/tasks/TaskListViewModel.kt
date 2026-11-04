package com.logunovinc.workshopmanager.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.warehouse.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TaskListUiState(
    val loading: Boolean = true, val refreshing: Boolean = false, val loadingMore: Boolean = false,
    val warehouse: Warehouse? = null, val filter: TaskFilter = TaskFilter(),
    val items: List<Task> = emptyList(), val total: Long = 0, val nextOffset: Long = 0,
    val hasMore: Boolean = false, val error: AppError? = null,
)
sealed interface TaskListIntent {
    data class Completed(val commandId: String) : TaskListIntent
    data object Refresh : TaskListIntent
    data object Poll : TaskListIntent
    data object LoadMore : TaskListIntent
    data class FilterChanged(val filter: TaskFilter) : TaskListIntent
}
@HiltViewModel
class TaskListViewModel @Inject constructor(private val resolve: ResolveWorkshopUseCase, private val loadTasks: LoadTasksUseCase) : ViewModel() {
    private val mutableState = MutableStateFlow(TaskListUiState())
    val uiState = mutableState.asStateFlow()
    private var lastCompletedCommandId: String? = null
    private var generation = 0L
    private var job: Job? = null
    init { refresh() }
    fun onIntent(intent: TaskListIntent) {
        when (intent) {
            is TaskListIntent.Completed -> {
                if (lastCompletedCommandId == intent.commandId) return
                lastCompletedCommandId = intent.commandId
                refresh()
            }
            TaskListIntent.Refresh -> refresh()
            TaskListIntent.Poll -> if (!uiState.value.loading && !uiState.value.refreshing && !uiState.value.loadingMore) refresh()
            TaskListIntent.LoadMore -> loadMore()
            is TaskListIntent.FilterChanged -> {
                if (intent.filter == uiState.value.filter) return
                mutableState.update { it.copy(filter = intent.filter, items = emptyList(), total = 0, nextOffset = 0, hasMore = false) }
                refresh()
            }
        }
    }
    private fun refresh() {
        val request = ++generation
        job?.cancel()
        val filter = uiState.value.filter
        mutableState.update { it.copy(loading = it.items.isEmpty(), refreshing = it.items.isNotEmpty(), loadingMore = false, error = null) }
        job = viewModelScope.launch {
            when (val workshop = resolve()) {
                is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loading = false, refreshing = false, warehouse = null, items = emptyList(), total = 0, nextOffset = 0, hasMore = false, error = workshop.error) }
                is Outcome.Success -> when (val page = loadTasks(workshop.value.id, filter)) {
                    is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loading = false, refreshing = false, warehouse = workshop.value, error = page.error) }
                    is Outcome.Success -> if (request == generation) mutableState.update { it.copy(loading = false, refreshing = false, warehouse = workshop.value,
                        items = page.value.items, total = page.value.total, nextOffset = page.value.nextOffset, hasMore = page.value.hasMore, error = null) }
                }
            }
        }
    }
    private fun loadMore() {
        val current = uiState.value
        val warehouse = current.warehouse ?: return
        if (current.loading || current.refreshing || current.loadingMore || !current.hasMore) return
        val request = generation
        mutableState.update { it.copy(loadingMore = true, error = null) }
        job = viewModelScope.launch {
            when (val page = loadTasks(warehouse.id, current.filter, current.nextOffset)) {
                is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loadingMore = false, error = page.error) }
                is Outcome.Success -> if (request == generation) mutableState.update { it.copy(loadingMore = false,
                    items = (it.items + page.value.items).distinctBy(Task::id), total = page.value.total, nextOffset = page.value.nextOffset, hasMore = page.value.hasMore) }
            }
        }
    }
}
