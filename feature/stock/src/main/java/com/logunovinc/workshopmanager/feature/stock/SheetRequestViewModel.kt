package com.logunovinc.workshopmanager.feature.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.order.*
import com.logunovinc.workshopmanager.domain.pending.*
import com.logunovinc.workshopmanager.domain.stock.SearchProductsUseCase
import com.logunovinc.workshopmanager.domain.stock.isFlatSheet
import com.logunovinc.workshopmanager.domain.warehouse.ResolveWorkshopUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SheetRequestUiState(
    val loading: Boolean = true, val requests: List<SheetRequest> = emptyList(), val error: AppError? = null,
    val query: String = "", val choices: List<com.logunovinc.workshopmanager.domain.stock.Product> = emptyList(),
    val length: String = "", val count: String = "", val chosen: Long? = null, val sending: Boolean = false,
    val pending: List<PendingCommand> = emptyList(), val confirmation: SheetRequestIntent? = null,
)
sealed interface SheetRequestIntent {
    data object Refresh : SheetRequestIntent
    data class Query(val value: String) : SheetRequestIntent
    data class Choose(val productId: Long) : SheetRequestIntent
    data class Length(val value: String) : SheetRequestIntent
    data class Count(val value: String) : SheetRequestIntent
    data object Submit : SheetRequestIntent
    data class Receive(val id: Long) : SheetRequestIntent
    data object Confirm : SheetRequestIntent
    data object Dismiss : SheetRequestIntent
    data class Retry(val commandId: String) : SheetRequestIntent
}
@HiltViewModel
class SheetRequestViewModel @Inject constructor(
    private val resolve: ResolveWorkshopUseCase,
    private val orders: WorkshopOrders,
    private val products: SearchProductsUseCase,
    pending: PendingCommandRepository,
    private val retryPending: RetryPendingCommandUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SheetRequestUiState())
    val uiState = mutableState.asStateFlow()
    private var warehouseId: Long? = null
    init {
        viewModelScope.launch { pending.observe().collect { commands ->
            mutableState.update { it.copy(pending = commands.filter { c -> c.action.startsWith("SHEET_") && c.state !in setOf(PendingState.ACKNOWLEDGED, PendingState.REJECTED) }) }
        } }
        refresh()
    }
    fun onIntent(intent: SheetRequestIntent) {
        if (uiState.value.sending) return
        if (uiState.value.confirmation != null && intent !in listOf(SheetRequestIntent.Confirm, SheetRequestIntent.Dismiss)) return
        when (intent) {
            SheetRequestIntent.Refresh -> refresh()
            is SheetRequestIntent.Query -> { mutableState.update { it.copy(query = intent.value) }; search(intent.value) }
            is SheetRequestIntent.Choose -> mutableState.update { it.copy(chosen = intent.productId) }
            is SheetRequestIntent.Length -> mutableState.update { it.copy(length = intent.value) }
            is SheetRequestIntent.Count -> mutableState.update { it.copy(count = intent.value) }
            SheetRequestIntent.Submit, is SheetRequestIntent.Receive -> if (uiState.value.pending.isEmpty()) mutableState.update { it.copy(confirmation = intent) }
            SheetRequestIntent.Dismiss -> mutableState.update { it.copy(confirmation = null) }
            SheetRequestIntent.Confirm -> {
                val confirm = uiState.value.confirmation
                mutableState.update { it.copy(confirmation = null) }
                if (confirm == SheetRequestIntent.Submit) submit()
                if (confirm is SheetRequestIntent.Receive) receive(confirm.id)
            }
            is SheetRequestIntent.Retry -> {
                mutableState.update { it.copy(sending = true) }
                viewModelScope.launch {
                    val result = retryPending(intent.commandId)
                    mutableState.update { it.copy(sending = false, error = when(result) { is CommandOutcome.Rejected -> result.error; is CommandOutcome.Unresolved -> result.error; else -> null }) }
                    refresh()
                }
            }
        }
    }
    private fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            when (val workshop = resolve()) {
                is Outcome.Failure -> mutableState.update { it.copy(loading = false, error = workshop.error) }
                is Outcome.Success -> {
                    warehouseId = workshop.value.id
                    when (val list = orders.sheetRequests(workshop.value.id)) {
                        is Outcome.Failure -> mutableState.update { it.copy(loading = false, error = list.error) }
                        is Outcome.Success -> mutableState.update { it.copy(loading = false, requests = list.value) }
                    }
                }
            }
        }
    }
    private fun search(query: String) {
        viewModelScope.launch {
            when (val page = orders.sheetCatalog()) {
                is Outcome.Failure -> mutableState.update { it.copy(error = page.error) }
                is Outcome.Success -> if (uiState.value.query == query) mutableState.update { it.copy(choices = page.value.filter { p -> p.name.contains(query, ignoreCase = true) }) }
            }
        }
    }
    private fun submit() {
        val state = uiState.value
        val warehouse = warehouseId ?: return
        val productId = state.chosen ?: return
        val length = state.length.trim().replace(',', '.').toBigDecimalOrNull() ?: return
        val count = state.count.toIntOrNull() ?: return
        val choice = state.choices.firstOrNull { it.id == productId } ?: return
        if (!choice.isFlatSheet() || state.sending) return
        mutableState.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            when (val result = orders.requestSheet(warehouse, productId, length, count)) {
                is CommandOutcome.Confirmed -> mutableState.update { it.copy(sending = false, length = "", count = "", chosen = null) }
                is CommandOutcome.Rejected -> mutableState.update { it.copy(sending = false, error = result.error) }
                is CommandOutcome.Unresolved -> mutableState.update { it.copy(sending = false, error = result.error) }
            }
            refresh()
        }
    }
    private fun receive(id: Long) {
        val request = uiState.value.requests.firstOrNull { it.id == id } ?: return
        if (uiState.value.sending || uiState.value.pending.isNotEmpty()) return
        mutableState.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            when (val result = orders.receiveSheet(request)) {
                is CommandOutcome.Rejected -> mutableState.update { it.copy(error = result.error) }
                is CommandOutcome.Unresolved -> mutableState.update { it.copy(error = result.error) }
                is CommandOutcome.Confirmed -> Unit
            }
            mutableState.update { it.copy(sending = false) }
            refresh()
        }
    }
}
