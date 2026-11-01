package com.logunovinc.workshopmanager.feature.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.stock.*
import com.logunovinc.workshopmanager.domain.warehouse.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StockListUiState(val loading: Boolean = true, val refreshing: Boolean = false, val loadingMore: Boolean = false,
    val warehouse: Warehouse? = null, val filter: StockFilter = StockFilter(), val items: List<StockPosition> = emptyList(),
    val total: Long = 0, val nextOffset: Long = 0, val hasMore: Boolean = false, val error: AppError? = null)
sealed interface StockListIntent {
    data object Refresh : StockListIntent
    data object LoadMore : StockListIntent
    data class QueryChanged(val value: String) : StockListIntent
    data class OnlyAvailableChanged(val value: Boolean) : StockListIntent
}
@HiltViewModel
class StockListViewModel @Inject constructor(private val resolve: ResolveWorkshopUseCase, private val load: LoadStockUseCase) : ViewModel() {
    private val mutableState = MutableStateFlow(StockListUiState())
    val uiState = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    init { refresh() }
    fun onIntent(intent: StockListIntent) {
        when (intent) {
            StockListIntent.Refresh -> refresh()
            StockListIntent.LoadMore -> more()
            is StockListIntent.QueryChanged -> {
                mutableState.update { it.copy(filter = it.filter.copy(query = intent.value), items = emptyList(), nextOffset = 0, total = 0, hasMore = false) }
                refresh(debounce = true)
            }
            is StockListIntent.OnlyAvailableChanged -> {
                mutableState.update { it.copy(filter = it.filter.copy(onlyAvailable = intent.value), items = emptyList(), nextOffset = 0, total = 0, hasMore = false) }
                refresh()
            }
        }
    }
    private fun refresh(debounce: Boolean = false) {
        val request = ++generation
        job?.cancel()
        val filter = uiState.value.filter
        mutableState.update { it.copy(loading = it.items.isEmpty(), refreshing = it.items.isNotEmpty(), loadingMore = false, error = null) }
        job = viewModelScope.launch {
            if (debounce) delay(300)
            when (val warehouse = resolve()) {
                is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loading = false, refreshing = false, warehouse = null, items = emptyList(), total = 0, hasMore = false, error = warehouse.error) }
                is Outcome.Success -> when (val page = load(warehouse.value.id, filter)) {
                    is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loading = false, refreshing = false, warehouse = warehouse.value, error = page.error) }
                    is Outcome.Success -> if (request == generation) mutableState.update { it.copy(loading = false, refreshing = false, warehouse = warehouse.value,
                        items = page.value.items, total = page.value.total, nextOffset = page.value.nextOffset, hasMore = page.value.hasMore) }
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
            when (val page = load(warehouse.id, current.filter, current.nextOffset)) {
                is Outcome.Failure -> if (request == generation) mutableState.update { it.copy(loadingMore = false, error = page.error) }
                is Outcome.Success -> if (request == generation) mutableState.update { it.copy(loadingMore = false,
                    items = (it.items + page.value.items).distinctBy(StockPosition::productId), total = page.value.total, nextOffset = page.value.nextOffset, hasMore = page.value.hasMore) }
            }
        }
    }
}
