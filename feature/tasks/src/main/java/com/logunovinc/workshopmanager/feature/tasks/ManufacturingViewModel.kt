package com.logunovinc.workshopmanager.feature.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.production.*
import com.logunovinc.workshopmanager.domain.pending.*
import com.logunovinc.workshopmanager.domain.stock.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.math.BigDecimal
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PrototypeMaterialRow(val id: Long, val product: Product, val input: PrototypeMaterialInput,
    val definition: StockDefinition? = null, val loading: Boolean = true, val error: AppError? = null)
data class ManufacturingPreviewBinding(val command: ManufacturingCommand, val preview: ManufacturingPreview, val revision: Long)
data class ManufacturingUiState(val loading: Boolean = true, val refreshing: Boolean = false, val stale: Boolean = true, val task: Task? = null,
    val output: ActualLineInput? = null, val materials: List<PrototypeMaterialRow> = emptyList(),
    val previewLoading: Boolean = false, val binding: ManufacturingPreviewBinding? = null, val reviewOpen: Boolean = false,
    val result: ExecutionResultState = ExecutionResultState.Idle, val pending: PendingCommand? = null, val error: AppError? = null,
    val materialSelecting: Boolean = false, val catalogQuery: String = "", val catalog: List<StockPosition> = emptyList(), val catalogLoading: Boolean = false,
    val catalogOffset: Long = 0, val catalogMore: Boolean = false, val catalogError: AppError? = null,
) {
    val prototype: Boolean get() = task?.production?.status == RecipeStatus.AWAITING_PROTOTYPE
    val canRefresh: Boolean get() = !loading && result != ExecutionResultState.Sending && !previewLoading && !reviewOpen
    val editable: Boolean get() = !loading && !stale && task?.type == TaskType.MANUFACTURE_PRODUCT && task.canExecute &&
        result != ExecutionResultState.Sending && result !is ExecutionResultState.Unresolved && pending == null && !reviewOpen
}
enum class PrototypeMaterialField { QUANTITY, SHEETS, UNIT_NAME, UNIT_QUANTITY }
sealed interface ManufacturingIntent {
    data class OutputQuantityChanged(val value: String) : ManufacturingIntent
    data class OutputSheetsChanged(val value: String) : ManufacturingIntent
    data class MaterialChanged(val rowId: Long, val field: PrototypeMaterialField, val value: String) : ManufacturingIntent
    data class AddMaterial(val productId: Long, val sheetLength: BigDecimal? = null) : ManufacturingIntent
    data class RemoveMaterial(val rowId: Long) : ManufacturingIntent
    data class CatalogQueryChanged(val value: String) : ManufacturingIntent
    data object MoreCatalog : ManufacturingIntent
    data object PreviewClick : ManufacturingIntent
    data object ReviewClick : ManufacturingIntent
    data object DismissReview : ManufacturingIntent
    data object ConfirmClick : ManufacturingIntent
    data object PullRefresh : ManufacturingIntent
    data object Refresh : ManufacturingIntent
}
@HiltViewModel
class ManufacturingViewModel @Inject constructor(savedStateHandle: SavedStateHandle,
    private val loadTask: LoadTaskUseCase, private val prepare: PrepareManufacturingUseCase,
    private val preview: PreviewManufacturingUseCase, private val execute: ExecuteManufacturingUseCase,
    private val loadStock: LoadStockUseCase, private val loadProduct: LoadProductUseCase, private val loadDefinition: LoadStockDefinitionUseCase,
    pending: PendingCommandRepository,
) : ViewModel() {
    private val taskId: Long = checkNotNull(savedStateHandle["taskId"])
    private val mutableState = MutableStateFlow(ManufacturingUiState())
    val uiState = mutableState.asStateFlow()
    private var revision = 0L
    private var loadGeneration = 0L
    private var catalogGeneration = 0L
    private var rowSequence = 0L
    private var previewJob: Job? = null
    private var catalogJob: Job? = null
    init {
        viewModelScope.launch { pending.observe().collect { commands ->
            val current = uiState.value
            val unresolvedId = (current.result as? ExecutionResultState.Unresolved)?.command?.id ?: current.pending?.id
            val recovered = commands.firstOrNull { it.id == unresolvedId && it.state == PendingState.ACKNOWLEDGED }?.confirmation
            val blocked = commands.firstOrNull { it.taskId == taskId && !it.action.startsWith("REQUEST_") && !it.action.startsWith("SHEET_") && it.state !in setOf(PendingState.ACKNOWLEDGED, PendingState.REJECTED) }
            mutableState.update { it.copy(pending = blocked, result = recovered?.let(ExecutionResultState::Confirmed) ?: it.result) }
            if (recovered != null) refresh(reset = true)
        } }
        refresh(reset = true)
    }
    private fun invalidatePreview() {
        revision++
        previewJob?.cancel()
        mutableState.update { it.copy(binding = null, previewLoading = false, reviewOpen = false, error = null) }
    }
    fun onIntent(intent: ManufacturingIntent) {
        val current = uiState.value
        when (intent) {
            is ManufacturingIntent.OutputQuantityChanged -> if (current.editable) { invalidatePreview(); mutableState.update { it.copy(output = it.output?.copy(quantity = intent.value)) } }
            is ManufacturingIntent.OutputSheetsChanged -> if (current.editable) { invalidatePreview(); mutableState.update { it.copy(output = it.output?.copy(sheets = intent.value)) } }
            is ManufacturingIntent.MaterialChanged -> if (current.editable && current.prototype) {
                invalidatePreview()
                mutableState.update { it.copy(materials = it.materials.map { row -> if (row.id != intent.rowId) row else row.copy(input = when (intent.field) {
                    PrototypeMaterialField.QUANTITY -> row.input.copy(quantity = intent.value)
                    PrototypeMaterialField.SHEETS -> row.input.copy(sheets = intent.value)
                    PrototypeMaterialField.UNIT_NAME -> row.input.copy(unitName = intent.value)
                    PrototypeMaterialField.UNIT_QUANTITY -> row.input.copy(unitQuantity = intent.value)
                }) }) }
            }
            is ManufacturingIntent.AddMaterial -> if (current.editable && current.prototype) selectMaterial(intent.productId, intent.sheetLength)
            is ManufacturingIntent.RemoveMaterial -> if (current.editable && current.prototype) { invalidatePreview(); mutableState.update { it.copy(materials = it.materials.filterNot { row -> row.id == intent.rowId }) } }
            is ManufacturingIntent.CatalogQueryChanged -> if (current.editable && current.prototype) {
                mutableState.update { it.copy(catalogQuery = intent.value, catalog = emptyList(), catalogMore = false, catalogOffset = 0) }
                catalog(reset = true, debounce = true)
            }
            ManufacturingIntent.MoreCatalog -> if (current.editable && current.prototype) catalog(reset = false)
            ManufacturingIntent.PreviewClick -> requestPreview()
            ManufacturingIntent.ReviewClick -> if (current.editable && current.binding?.revision == revision && current.binding.preview.canExecute) mutableState.update { it.copy(reviewOpen = true) }
            ManufacturingIntent.DismissReview -> if (current.result != ExecutionResultState.Sending) mutableState.update { it.copy(reviewOpen = false) }
            ManufacturingIntent.ConfirmClick -> confirm()
            ManufacturingIntent.Refresh -> if (current.canRefresh) refresh(reset = false)
            ManufacturingIntent.PullRefresh -> if (current.canRefresh) refresh(reset = false, manual = true)
        }
    }
    private fun draft(state: ManufacturingUiState): ManufacturingDraft? = state.output?.let { ManufacturingDraft(it, if (state.prototype) state.materials.map(PrototypeMaterialRow::input) else emptyList()) }
    private fun requestPreview() {
        val current = uiState.value
        val task = current.task ?: return
        val draft = draft(current) ?: return
        if (!current.editable || current.previewLoading || current.materialSelecting || current.materials.any { it.loading || it.error != null }) return
        val command = when (val prepared = prepare(task, draft)) {
            is Outcome.Failure -> { mutableState.update { it.copy(error = prepared.error) }; return }
            is Outcome.Success -> prepared.value
        }
        val requestRevision = revision
        mutableState.update { it.copy(previewLoading = true, binding = null, reviewOpen = false, error = null) }
        previewJob = viewModelScope.launch {
            when (val result = preview(task, command)) {
                is Outcome.Success -> if (revision == requestRevision) mutableState.update { it.copy(previewLoading = false, binding = ManufacturingPreviewBinding(command, result.value, requestRevision)) }
                is Outcome.Failure -> if (revision == requestRevision) {
                    mutableState.update { it.copy(previewLoading = false, error = result.error) }
                    if (result.error.kind == ErrorKind.CONFLICT) refresh(reset = false, operationError = result.error)
                }
            }
        }
    }
    private fun confirm() {
        val current = uiState.value
        val task = current.task ?: return
        val binding = current.binding ?: return
        if (!current.reviewOpen || current.loading || current.stale || current.pending != null || current.result == ExecutionResultState.Sending || binding.revision != revision || !binding.preview.canExecute) return
        val fresh = draft(current)?.let { prepare(task, it) }
        if (fresh !is Outcome.Success || fresh.value != binding.command) { invalidatePreview(); return }
        mutableState.update { it.copy(result = ExecutionResultState.Sending) }
        viewModelScope.launch {
            val result = execute(task, binding.command, binding.preview, comment = "")
            mutableState.update { it.copy(result = result.executionState(), binding = null, reviewOpen = false) }
            when (result) {
                is CommandOutcome.Confirmed -> refresh(reset = true)
                is CommandOutcome.Rejected -> refresh(reset = false, operationError = result.error)
                is CommandOutcome.Unresolved -> Unit
            }
        }
    }
    private fun refresh(reset: Boolean, operationError: AppError? = null, manual: Boolean = false) {
        invalidatePreview()
        val generation = ++loadGeneration
        mutableState.update { it.copy(loading = true, refreshing = manual, stale = true, error = operationError) }
        viewModelScope.launch {
            when (val task = loadTask(taskId)) {
                is Outcome.Failure -> if (generation == loadGeneration) mutableState.update { it.copy(loading = false, refreshing = false, error = task.error) }
                is Outcome.Success -> if (generation == loadGeneration) {
                    mutableState.update { state ->
                    val output = task.value.lines.filter { it.role == "output" }.singleOrNull()
                    val prototype = task.value.production?.status == RecipeStatus.AWAITING_PROTOTYPE
                    state.copy(loading = false, refreshing = false, stale = false, task = task.value,
                        output = output?.let { line -> if (!reset && state.output?.taskLineId == line.id) state.output else ActualLineInput(line.id) },
                        materials = if (reset || !prototype) emptyList() else state.materials)
                    }
                    if (uiState.value.prototype && uiState.value.editable) catalog(reset = true)
                }
            }
        }
    }
    private fun selectMaterial(productId: Long, requestedLength: BigDecimal?) {
        if (uiState.value.materialSelecting || uiState.value.catalog.none { it.productId == productId }) return
        val position = uiState.value.catalog.first { it.productId == productId }
        val lengths = position.dimensions.filter { it.sheets > 0 && it.length.signum() > 0 }.map { it.length }
        val selectedLength = requestedLength ?: lengths.singleOrNull()
        if (requestedLength != null && lengths.none { it.compareTo(requestedLength) == 0 }) return
        invalidatePreview()
        val generation = catalogGeneration
        mutableState.update { it.copy(materialSelecting = true, catalogError = null) }
        viewModelScope.launch {
            when (val result = loadProduct(productId)) {
                is Outcome.Success -> if (generation == catalogGeneration && uiState.value.editable && uiState.value.prototype) {
                    if (result.value.sheet && selectedLength == null) {
                        mutableState.update { it.copy(catalogError = AppError(ErrorKind.VALIDATION, "Нет остатка с известной длиной листа")) }
                    } else {
                        addMaterial(result.value, if (result.value.sheet) selectedLength else null)
                        catalogGeneration++
                        catalogJob?.cancel()
                        mutableState.update { it.copy(catalog = emptyList(), catalogMore = false, catalogLoading = false, catalogOffset = 0) }
                    }
                }
                is Outcome.Failure -> if (generation == catalogGeneration) mutableState.update { it.copy(catalogError = result.error) }
            }
            mutableState.update { it.copy(materialSelecting = false) }
        }
    }
    private fun addMaterial(product: Product, sheetLength: BigDecimal?) {
        invalidatePreview()
        val id = ++rowSequence
        mutableState.update { it.copy(materials = it.materials + PrototypeMaterialRow(id, product, PrototypeMaterialInput(product.id, sheetLength = sheetLength?.toPlainString().orEmpty()))) }
        viewModelScope.launch {
            when (val result = loadDefinition(product.id)) {
                is Outcome.Success -> mutableState.update { it.copy(materials = it.materials.map { row -> if (row.id == id) row.copy(definition = result.value, loading = false) else row }) }
                is Outcome.Failure -> mutableState.update { it.copy(materials = it.materials.map { row -> if (row.id == id) row.copy(error = result.error, loading = false) else row }) }
            }
        }
    }
    private fun catalog(reset: Boolean, debounce: Boolean = false) {
        val state = uiState.value
        if (state.catalogQuery.isBlank()) {
            catalogGeneration++
            catalogJob?.cancel()
            mutableState.update { it.copy(catalog = emptyList(), catalogLoading = false, catalogMore = false, catalogOffset = 0, catalogError = null) }
            return
        }
        if (!reset && (state.catalogLoading || !state.catalogMore)) return
        if (reset) { catalogGeneration++; catalogJob?.cancel() }
        val generation = catalogGeneration
        val warehouseId = state.task?.warehouseId ?: return
        val query = state.catalogQuery
        mutableState.update { it.copy(catalogLoading = true, catalogError = null, catalog = if (reset) emptyList() else it.catalog) }
        catalogJob = viewModelScope.launch {
            if (debounce) delay(300)
            var offset = if (reset) 0L else state.catalogOffset
            while (generation == catalogGeneration) {
                when (val page = loadStock(warehouseId, StockFilter(query, onlyAvailable = false), offset)) {
                    is Outcome.Failure -> {
                        if (generation == catalogGeneration) mutableState.update { it.copy(catalogLoading = false, catalogError = page.error) }
                        return@launch
                    }
                    is Outcome.Success -> {
                        if (generation != catalogGeneration) return@launch
                        val present = page.value.items.filter { position -> position.quantity.signum() > 0 || position.dimensions.any { it.sheets > 0 } }
                        val more = page.value.hasMore && page.value.nextOffset > offset
                        mutableState.update { it.copy(catalogLoading = present.isEmpty() && more,
                            catalog = (it.catalog + present).distinctBy(StockPosition::productId), catalogOffset = page.value.nextOffset, catalogMore = more) }
                        if (present.isNotEmpty() || !more) return@launch
                        offset = page.value.nextOffset
                    }
                }
            }
        }
    }
}
