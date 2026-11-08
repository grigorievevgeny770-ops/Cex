package com.logunovinc.workshopmanager.feature.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.order.*
import com.logunovinc.workshopmanager.domain.pending.*
import com.logunovinc.workshopmanager.domain.stock.LoadProductUseCase
import com.logunovinc.workshopmanager.domain.stock.SearchProductsUseCase
import com.logunovinc.workshopmanager.domain.stock.isFlatSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class OrderDetailUiState(
    val loading: Boolean = true, val order: WorkshopOrder? = null, val error: AppError? = null,
    val pending: PendingCommand? = null, val sending: Boolean = false, val retrying: Boolean = false,
    val confirm: OrderConfirm? = null, val materials: List<SheetUse> = emptyList(),
    val sheetQuery: String = "", val sheetChoices: List<com.logunovinc.workshopmanager.domain.stock.Product> = emptyList(),
    val sheetLength: String = "", val sheetCount: String = "",
    val lastConfirmation: CommandConfirmation? = null,
    val drawings: Map<Long,ProfileDrawing> = emptyMap(), val drawingErrors: Map<Long,AppError> = emptyMap(),
    val areaPreview:AreaPreview?=null,val previewError:AppError?=null,
)
sealed interface OrderConfirm { data object Start : OrderConfirm; data object Manufacture : OrderConfirm; data object ManufactureArea : OrderConfirm; data object HandOver : OrderConfirm; data class Reverse(val target: ReverseTarget) : OrderConfirm }
sealed interface OrderDetailIntent {
    data object Refresh : OrderDetailIntent
    data object Retry : OrderDetailIntent
    data object Start : OrderDetailIntent
    data object Manufacture : OrderDetailIntent
    data object ManufactureArea : OrderDetailIntent
    data object HandOver : OrderDetailIntent
    data object ReverseManufacture : OrderDetailIntent
    data object ReverseHandOver : OrderDetailIntent
    data object Dismiss : OrderDetailIntent
    data object Confirm : OrderDetailIntent
    data class SheetQuery(val value: String) : OrderDetailIntent
    data class ChooseSheet(val productId: Long) : OrderDetailIntent
    data class SheetLength(val value: String) : OrderDetailIntent
    data class SheetCount(val value: String) : OrderDetailIntent
    data object AddSheet : OrderDetailIntent
    data class RemoveSheet(val index: Int) : OrderDetailIntent
}

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val orders: WorkshopOrders,
    private val products: SearchProductsUseCase,
    private val product: LoadProductUseCase,
    pending: PendingCommandRepository,
    private val retryPending: RetryPendingCommandUseCase,
) : ViewModel() {
    private val orderId: Long = checkNotNull(savedStateHandle["orderId"])
    private val mutableState = MutableStateFlow(OrderDetailUiState())
    val uiState = mutableState.asStateFlow()
    private var chosenSheet: Long? = null
    private var refreshGeneration=0L
    init {
        viewModelScope.launch { pending.observe().collect { commands ->
            val blocked = commands.firstOrNull { it.taskId == orderId && it.action.startsWith("REQUEST_") && it.state !in setOf(PendingState.ACKNOWLEDGED, PendingState.REJECTED) }
            val recovered = commands.firstOrNull { it.id == uiState.value.pending?.id && it.state == PendingState.ACKNOWLEDGED }?.confirmation
            mutableState.update { it.copy(pending = blocked, lastConfirmation = recovered ?: it.lastConfirmation) }
            if (recovered != null) refresh()
        } }
        refresh()
    }
    fun onIntent(intent: OrderDetailIntent) {
        val state = uiState.value
        if (state.sending || state.retrying || (state.loading && intent != OrderDetailIntent.Refresh)) return
        when (intent) {
            OrderDetailIntent.Refresh -> if (state.confirm == null) refresh()
            OrderDetailIntent.Retry -> retry()
            OrderDetailIntent.Start -> if (state.order?.canStart == true && state.pending == null) mutableState.update { it.copy(confirm = OrderConfirm.Start, error = null) }
            OrderDetailIntent.Manufacture -> if (state.order?.canManufacture == true && state.pending == null) mutableState.update { it.copy(confirm = OrderConfirm.Manufacture, error = null) }
            OrderDetailIntent.ManufactureArea -> if (state.order?.canManufactureArea == true && state.pending == null && state.areaPreview?.matches(state.order)==true && state.order.lines.all { line -> line.drawingVersion != null && state.drawings[line.id]?.version == line.drawingVersion }) mutableState.update { it.copy(confirm=OrderConfirm.ManufactureArea,error=null) }
            OrderDetailIntent.HandOver -> if (state.order?.canHandOver == true && state.pending == null) mutableState.update { it.copy(confirm = OrderConfirm.HandOver, error = null) }
            OrderDetailIntent.ReverseManufacture -> if (state.order?.canReverseManufacture == true && state.pending == null) mutableState.update { it.copy(confirm = OrderConfirm.Reverse(ReverseTarget.MANUFACTURE), error = null) }
            OrderDetailIntent.ReverseHandOver -> if (state.order?.canReverseHandOver == true && state.pending == null) mutableState.update { it.copy(confirm = OrderConfirm.Reverse(ReverseTarget.HAND_OVER), error = null) }
            OrderDetailIntent.Dismiss -> if (!state.sending) mutableState.update { it.copy(confirm = null) }
            OrderDetailIntent.Confirm -> confirm()
            is OrderDetailIntent.SheetQuery -> { mutableState.update { it.copy(sheetQuery = intent.value) }; searchSheets(intent.value) }
            is OrderDetailIntent.ChooseSheet -> chosenSheet = intent.productId
            is OrderDetailIntent.SheetLength -> mutableState.update { it.copy(sheetLength = intent.value) }
            is OrderDetailIntent.SheetCount -> mutableState.update { it.copy(sheetCount = intent.value) }
            OrderDetailIntent.AddSheet -> addSheet()
            is OrderDetailIntent.RemoveSheet -> mutableState.update { it.copy(materials = it.materials.filterIndexed { index, _ -> index != intent.index }) }
        }
    }
    private fun refresh(clearError:Boolean=true) {
        val generation=++refreshGeneration
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = if(clearError)null else it.error,areaPreview=null,previewError=null) }
            when (val result = orders.order(orderId)) {
                is Outcome.Failure -> if(generation==refreshGeneration) mutableState.update { it.copy(loading = false, error = result.error) }
                is Outcome.Success -> {
                    val drawings=mutableMapOf<Long,ProfileDrawing>();val failures=mutableMapOf<Long,AppError>()
                    for(line in result.value.lines.filter { it.drawingVersion != null }) {
                        when(val drawing=orders.drawing(orderId,line.id)) {
                            is Outcome.Failure -> failures[line.id]=drawing.error
                            is Outcome.Success -> if(drawing.value.version==line.drawingVersion) drawings[line.id]=drawing.value else failures[line.id]=AppError(ErrorKind.CONFLICT,"Чертёж изменился. Обновите заявку")
                        }
                    }
                    var preview:AreaPreview?=null;var previewError:AppError?=null
                    if(result.value.canManufactureArea && failures.isEmpty() && result.value.lines.all { it.drawingVersion!=null && drawings[it.id]?.version==it.drawingVersion }) {
                        when(val read=orders.areaPreview(orderId)) {
                            is Outcome.Failure -> previewError=read.error
                            is Outcome.Success -> if(read.value.matches(result.value))preview=read.value else previewError=AppError(ErrorKind.CONFLICT,"Расход или чертёж изменился. Обновите заявку")
                        }
                    }
                    if(generation==refreshGeneration) mutableState.update { it.copy(loading=false,order=result.value,drawings=drawings,drawingErrors=failures,areaPreview=preview,previewError=previewError,confirm=null) }
                }
            }
        }
    }
    private fun searchSheets(query: String) {
        viewModelScope.launch {
            when (val page = orders.sheetCatalog()) {
                is Outcome.Failure -> mutableState.update { it.copy(error = page.error) }
                is Outcome.Success -> if (uiState.value.sheetQuery == query) mutableState.update { it.copy(sheetChoices = page.value.filter { p -> p.name.contains(query, ignoreCase = true) }) }
            }
        }
    }
    private fun addSheet() {
        val state = uiState.value
        val productId = chosenSheet ?: return
        val length = state.sheetLength.trim().replace(',', '.').toBigDecimalOrNull() ?: return
        val count = state.sheetCount.toIntOrNull() ?: return
        viewModelScope.launch {
            when (val loaded = product(productId)) {
                is Outcome.Failure -> mutableState.update { it.copy(error = loaded.error) }
                is Outcome.Success -> if (!loaded.value.isFlatSheet()) mutableState.update { it.copy(error = AppError(ErrorKind.VALIDATION, "Для расхода можно выбрать только плоский лист")) }
                    else mutableState.update { it.copy(materials = it.materials + SheetUse(productId, count, length), sheetLength = "", sheetCount = "", error = null) }
            }
        }
    }
    private fun confirm() {
        val state = uiState.value
        val order = state.order ?: return
        val confirm = state.confirm ?: return
        if (state.sending || state.pending != null) return
        mutableState.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val result = when (confirm) {
                OrderConfirm.Start -> orders.start(order)
                OrderConfirm.Manufacture -> orders.manufacture(order, state.materials)
                OrderConfirm.ManufactureArea -> orders.manufactureArea(order,state.areaPreview)
                OrderConfirm.HandOver -> orders.handOver(order)
                is OrderConfirm.Reverse -> orders.reverse(order, confirm.target)
            }
            when (result) {
                is CommandOutcome.Confirmed -> mutableState.update { it.copy(sending = false, confirm = null, materials = emptyList(), lastConfirmation = result.confirmation) }
                is CommandOutcome.Rejected -> mutableState.update { it.copy(sending = false, confirm = null, error = result.error) }
                is CommandOutcome.Unresolved -> mutableState.update { it.copy(sending = false, confirm = null, pending = result.command, error = result.error) }
            }
            refresh(clearError=false)
        }
    }
    private fun retry() {
        if (uiState.value.retrying || uiState.value.sending) return
        val pending = uiState.value.pending ?: return
        mutableState.update { it.copy(retrying = true) }
        viewModelScope.launch {
            when (val result = retryPending(pending.id)) {
                is CommandOutcome.Confirmed -> mutableState.update { it.copy(retrying = false, pending = null, lastConfirmation = result.confirmation) }
                is CommandOutcome.Rejected -> mutableState.update { it.copy(retrying = false, error = result.error) }
                is CommandOutcome.Unresolved -> mutableState.update { it.copy(retrying = false, pending = result.command, error = result.error) }
            }
            refresh(clearError=false)
        }
    }
}
