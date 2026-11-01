package com.logunovinc.workshopmanager.feature.stock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.stock.*
import com.logunovinc.workshopmanager.domain.warehouse.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StockDetailUiState(val loading: Boolean = true, val position: StockPosition? = null, val warehouse: Warehouse? = null, val error: AppError? = null)
sealed interface StockDetailIntent { data object Refresh : StockDetailIntent }
@HiltViewModel
class StockDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle,
    private val resolve: ResolveWorkshopUseCase, private val load: LoadStockPositionUseCase) : ViewModel() {
    private val productId: Long = checkNotNull(savedStateHandle["productId"])
    private val mutableState = MutableStateFlow(StockDetailUiState())
    val uiState = mutableState.asStateFlow()
    private var generation = 0L
    init { refresh() }
    fun onIntent(intent: StockDetailIntent) { when (intent) { StockDetailIntent.Refresh -> refresh() } }
    private fun refresh() {
        val request = ++generation
        mutableState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val warehouse = resolve()) {
                is Outcome.Failure -> if (request == generation) mutableState.value = StockDetailUiState(loading = false, error = warehouse.error)
                is Outcome.Success -> when (val position = load(warehouse.value.id, productId)) {
                    is Outcome.Failure -> if (request == generation) mutableState.value = StockDetailUiState(loading = false, warehouse = warehouse.value, error = position.error)
                    is Outcome.Success -> if (request == generation) mutableState.value = StockDetailUiState(loading = false, warehouse = warehouse.value, position = position.value)
                }
            }
        }
    }
}
