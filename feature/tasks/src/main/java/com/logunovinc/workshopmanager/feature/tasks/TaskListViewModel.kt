package com.logunovinc.workshopmanager.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.order.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.warehouse.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TaskListUiState(
    val loading: Boolean = true, val refreshing: Boolean = false, val pullRefreshing: Boolean = false, val loadingMore: Boolean = false,
    val warehouse: Warehouse? = null, val filter: TaskFilter = TaskFilter(),
    val items: List<Task> = emptyList(), val orders: List<WorkshopOrder>? = null, val total: Long = 0, val nextOffset: Long = 0,
    val hasMore: Boolean = false, val error: AppError? = null,
) {
    val orderMode: Boolean get() = orders != null
}
sealed interface TaskListIntent {
    data class Completed(val commandId: String) : TaskListIntent
    data object PullRefresh : TaskListIntent
    data object Refresh : TaskListIntent
    data object Poll : TaskListIntent
    data object LoadMore : TaskListIntent
    data class FilterChanged(val filter: TaskFilter) : TaskListIntent
    data class OrderViewChanged(val history: Boolean) : TaskListIntent
}
@HiltViewModel
class TaskListViewModel @Inject constructor(private val resolve: ResolveWorkshopUseCase, private val loadTasks: LoadTasksUseCase, private val ordersApi: WorkshopOrders) : ViewModel() {
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
            TaskListIntent.PullRefresh -> if (!uiState.value.loading && !uiState.value.refreshing && !uiState.value.loadingMore) refresh(manual = true)
            TaskListIntent.Poll -> if (!uiState.value.loading && !uiState.value.refreshing && !uiState.value.loadingMore) refresh()
            TaskListIntent.LoadMore -> loadMore()
            is TaskListIntent.FilterChanged -> {
                if (uiState.value.orderMode || intent.filter == uiState.value.filter) return
                mutableState.update { it.copy(filter = intent.filter, items = emptyList(), total = 0, nextOffset = 0, hasMore = false) }
                refresh()
            }
            is TaskListIntent.OrderViewChanged -> {
                if (!uiState.value.orderMode || (uiState.value.filter.view == TaskView.HISTORY) == intent.history) return
                mutableState.update { it.copy(filter = it.filter.copy(view = if (intent.history) TaskView.HISTORY else TaskView.ACTIVE), orders = emptyList(), total = 0, nextOffset = 0, hasMore = false) }
                refresh()
            }
        }
    }
    private fun refresh(manual: Boolean = false) {
        val visibleEmpty = uiState.value.orders?.isEmpty() ?: uiState.value.items.isEmpty()
        val retainedOffset = if (manual || visibleEmpty) 0L else uiState.value.nextOffset
        val request = ++generation
        job?.cancel()
        val filter = uiState.value.filter
        mutableState.update { it.copy(loading = visibleEmpty, refreshing = !visibleEmpty, pullRefreshing = manual, loadingMore = false, error = null) }
        job = viewModelScope.launch {
            when (val workshop = resolve()) {
                is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loading = false, refreshing = false, pullRefreshing = false, warehouse = null, items = emptyList(), orders = null, total = 0, nextOffset = 0, hasMore = false, error = workshop.error) }
                is Outcome.Success -> {
                    val orderMode = (ordersApi.contracts() as? Outcome.Success)?.value?.orders == true
                    if (!orderMode) when (val page = refreshRange(workshop.value.id, filter, retainedOffset)) {
                        is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loading = false, refreshing = false, pullRefreshing = false, warehouse = workshop.value, orders = null, error = page.error) }
                        is Outcome.Success -> if (request == generation) mutableState.update { it.copy(loading = false, refreshing = false, pullRefreshing = false, warehouse = workshop.value, orders = null,
                            items = page.value.items, total = page.value.total, nextOffset = page.value.nextOffset, hasMore = page.value.hasMore, error = null) }
                    } else when (val page = refreshOrders(workshop.value.id, if (filter.view == TaskView.HISTORY) OrderView.HISTORY else OrderView.ACTIVE, retainedOffset)) {
                        is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loading = false, refreshing = false, pullRefreshing = false, warehouse = workshop.value, error = page.error) }
                        is Outcome.Success -> if (request == generation) mutableState.update { it.copy(loading = false, refreshing = false, pullRefreshing = false, warehouse = workshop.value, items = emptyList(),
                            orders = page.value.items, total = page.value.total, nextOffset = page.value.nextOffset, hasMore = page.value.hasMore, error = null) }
                    }
                }
            }
        }
    }
    private data class OrderSnapshot(val items: List<WorkshopOrder>, val total: Long, val nextOffset: Long, val hasMore: Boolean)
    private suspend fun refreshOrders(warehouseId: Long, view: OrderView, retainedOffset: Long): Outcome<OrderSnapshot> {
        var offset = 0L
        val items = mutableListOf<WorkshopOrder>()
        while (true) {
            when (val result = ordersApi.orders(warehouseId, view, offset)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> {
                    val page = result.value
                    items.addAll(page.items)
                    if (!page.hasMore || page.nextOffset >= retainedOffset || page.nextOffset <= offset) {
                        return Outcome.Success(OrderSnapshot(items.distinctBy(WorkshopOrder::id), page.total, page.nextOffset, page.hasMore))
                    }
                    offset = page.nextOffset
                }
            }
        }
    }
    private data class RangeSnapshot(val items: List<Task>, val total: Long, val nextOffset: Long, val hasMore: Boolean)

    /** Refresh every previously loaded page so switching tabs preserves the scroll range. */
    private suspend fun refreshRange(warehouseId: Long, filter: TaskFilter, retainedOffset: Long): Outcome<RangeSnapshot> {
        var offset = 0L
        val items = mutableListOf<Task>()
        while (true) {
            when (val result = loadTasks(warehouseId, filter, offset)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> {
                    val page = result.value
                    items.addAll(page.items)
                    if (!page.hasMore || page.nextOffset >= retainedOffset || page.nextOffset <= offset) {
                        return Outcome.Success(RangeSnapshot(items.distinctBy(Task::id), page.total, page.nextOffset, page.hasMore))
                    }
                    offset = page.nextOffset
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
            if (current.orderMode) when (val page = ordersApi.orders(warehouse.id, if (current.filter.view == TaskView.HISTORY) OrderView.HISTORY else OrderView.ACTIVE, current.nextOffset)) {
                is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loadingMore = false, error = page.error) }
                is Outcome.Success -> if (request == generation) mutableState.update { it.copy(loadingMore = false,
                    orders = ((it.orders ?: emptyList()) + page.value.items).distinctBy(WorkshopOrder::id), total = page.value.total, nextOffset = page.value.nextOffset, hasMore = page.value.hasMore) }
            } else when (val page = loadTasks(warehouse.id, current.filter, current.nextOffset)) {
                is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loadingMore = false, error = page.error) }
                is Outcome.Success -> if (request == generation) mutableState.update { it.copy(loadingMore = false,
                    items = (it.items + page.value.items).distinctBy(Task::id), total = page.value.total, nextOffset = page.value.nextOffset, hasMore = page.value.hasMore) }
            }
        }
    }
}
