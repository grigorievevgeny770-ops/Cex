package com.logunovinc.workshopmanager.feature.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.stock.*
import com.logunovinc.workshopmanager.domain.task.Page
import com.logunovinc.workshopmanager.domain.warehouse.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StockListUiState(val loading: Boolean = true, val refreshing: Boolean = false, val pullRefreshing: Boolean = false, val loadingMore: Boolean = false,
    val warehouse: Warehouse? = null, val filter: StockFilter = StockFilter(), val items: List<StockPosition> = emptyList(),
    val total: Long = 0, val nextOffset: Long = 0, val hasMore: Boolean = false, val unverified: Int = 0, val error: AppError? = null)
sealed interface StockListIntent {
    data object PullRefresh : StockListIntent
    data object Refresh : StockListIntent
    data object LoadMore : StockListIntent
    data class QueryChanged(val value: String) : StockListIntent
}
@HiltViewModel
class StockListViewModel @Inject constructor(private val resolve: ResolveWorkshopUseCase, private val load: LoadStockUseCase, private val products: LoadProductUseCase) : ViewModel() {
    private val mutableState = MutableStateFlow(StockListUiState())
    val uiState = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    init { refresh() }
    fun onIntent(intent: StockListIntent) {
        when (intent) {
            StockListIntent.Refresh -> refresh()
            StockListIntent.PullRefresh -> if (!uiState.value.loading && !uiState.value.refreshing && !uiState.value.loadingMore) refresh(manual = true)
            StockListIntent.LoadMore -> more()
            is StockListIntent.QueryChanged -> {
                mutableState.update { it.copy(filter = it.filter.copy(query = intent.value, onlyAvailable = false), items = emptyList(), nextOffset = 0, total = 0, hasMore = false) }
                refresh(debounce = true)
            }

        }
    }
    private fun refresh(debounce: Boolean = false, manual: Boolean = false) {
        val retainedOffset = if (manual || uiState.value.items.isEmpty()) 0L else uiState.value.nextOffset
        val request = ++generation
        job?.cancel()
        val filter = uiState.value.filter.copy(onlyAvailable = false)
        mutableState.update { it.copy(loading = it.items.isEmpty(), refreshing = it.items.isNotEmpty(), pullRefreshing = manual, loadingMore = false, error = null) }
        job = viewModelScope.launch {
            if (debounce) delay(300)
            when (val warehouse = resolve()) {
                is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loading = false, refreshing = false, pullRefreshing = false, warehouse = null, items = emptyList(), total = 0, hasMore = false, unverified = 0, error = warehouse.error) }
                is Outcome.Success -> when (val page = refreshRange(warehouse.value.id, filter, retainedOffset)) {
                    is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loading = false, refreshing = false, pullRefreshing = false, warehouse = warehouse.value, error = page.error) }
                    is Outcome.Success -> if (request == generation) {
                        val classified = sheetsOf(page.value.items)
                        mutableState.update { it.copy(loading = false, refreshing = false, pullRefreshing = false, warehouse = warehouse.value,
                            items = classified.sheets, total = page.value.total, nextOffset = page.value.nextOffset, hasMore = page.value.hasMore, unverified = classified.unverified) }
                    }
                }
            }
        }
    }
    private data class RangeSnapshot(val items: List<StockPosition>, val total: Long, val nextOffset: Long, val hasMore: Boolean)

    /** Refresh every previously loaded page so switching tabs preserves the scroll range. */
    private suspend fun refreshRange(warehouseId: Long, filter: StockFilter, retainedOffset: Long): Outcome<RangeSnapshot> {
        var offset = 0L
        val items = mutableListOf<StockPosition>()
        while (true) {
            when (val result = load(warehouseId, filter, offset)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> {
                    val page = result.value
                    items.addAll(page.items)
                    if (!page.hasMore || page.nextOffset >= retainedOffset || page.nextOffset <= offset) {
                        return Outcome.Success(RangeSnapshot(items.distinctBy(StockPosition::productId), page.total, page.nextOffset, page.hasMore))
                    }
                    offset = page.nextOffset
                }
            }
        }
    }
    private fun more() {
        val current = uiState.value
        if (current.loading || current.refreshing || current.loadingMore || !current.hasMore) return
        val warehouse = current.warehouse ?: return
        val request = generation
        mutableState.update { it.copy(loadingMore = true, error = null) }
        job = viewModelScope.launch {
            when (val page = load(warehouse.id, current.filter.copy(onlyAvailable = false), current.nextOffset)) {
                is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loadingMore = false, error = page.error) }
                is Outcome.Success -> if (request == generation) {
                    val classified = sheetsOf(page.value.items)
                    mutableState.update { it.copy(loadingMore = false,
                        items = (it.items + classified.sheets).distinctBy(StockPosition::productId), total = page.value.total, nextOffset = page.value.nextOffset, hasMore = page.value.hasMore, unverified = it.unverified + classified.unverified) }
                }
            }
        }
    }
    /** Catalog flag only. A failed read is unverified and is not shown as flat sheet or as finished goods. */
    private suspend fun sheetsOf(positions: List<StockPosition>): FlatSheetClassification {
        val facts = mutableMapOf<Long, Product?>()
        for (id in positions.map(StockPosition::productId).distinct()) facts[id] = (products(id) as? Outcome.Success)?.value
        return classifyFlatSheet(positions, facts)
    }
}
