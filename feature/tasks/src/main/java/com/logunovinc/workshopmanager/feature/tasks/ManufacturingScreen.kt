package com.logunovinc.workshopmanager.feature.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logunovinc.workshopmanager.core.ui.QuantityFormatter
import com.logunovinc.workshopmanager.core.ui.RefreshableContent
import com.logunovinc.workshopmanager.domain.production.*
import com.logunovinc.workshopmanager.domain.stock.isFlatSheet

@Composable
fun ManufacturingRoute(viewModel: ManufacturingViewModel, onCompleted: (com.logunovinc.workshopmanager.domain.pending.CommandConfirmation) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompletedTaskEffect((state.result as? ExecutionResultState.Confirmed)?.receipt, onCompleted)
    ManufacturingScreen(state, viewModel::onIntent)
}
@Composable
fun ManufacturingScreen(state: ManufacturingUiState, onIntent: (ManufacturingIntent) -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val task = state.task
    val output = task?.lines?.filter { it.role == "output" }?.singleOrNull()
    RefreshableContent(state.refreshing, state.canRefresh, { onIntent(ManufacturingIntent.PullRefresh) }, automaticLoading = state.loading || state.previewLoading || state.catalogLoading || state.materialSelecting || state.materials.any { it.loading } || state.result == ExecutionResultState.Sending) {
        LazyColumn(Modifier.fillMaxSize().testTag("manufacturing-list").padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Изготовление", style = MaterialTheme.typography.headlineMedium)
                state.error?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error) }
                when (val result = state.result) {
                    is ExecutionResultState.Confirmed -> { Text("Готово", color = MaterialTheme.colorScheme.primary) }
                    is ExecutionResultState.Unresolved -> { Text("Исход неизвестен: ${result.error.operationalMessage()}", color = MaterialTheme.colorScheme.error) }
                    is ExecutionResultState.Rejected -> Text(result.error.operationalMessage(), color = MaterialTheme.colorScheme.error)
                    ExecutionResultState.Sending -> Unit
                    else -> Unit
                }
                state.pending?.let { pending -> Text("Не подтверждено: ${pending.summary.operationalTitle()}"); pending.summary.operationalLines().forEach { Text(it) } }
                if (task?.production?.supported != true || output == null) Text("Нужна настройка производства. Обратитесь к руководителю")
                if (output != null) {
                    Text(output.productName, style = MaterialTheme.typography.titleLarge)
                    Text("План ${QuantityFormatter.format(output.planned)} · изготовлено ${QuantityFormatter.format(output.actual)} · осталось ${QuantityFormatter.format(output.remaining)} ${output.baseUnit.orEmpty()}")
                    if (state.prototype) Text("Первый выпуск: укажите расход материалов. Он войдёт в план и задаст расход следующих партий")
                    else Text("Укажите, сколько изготовить")
                    val sheets = output.plannedSheets != null || output.remainingSheets != null
                    if (sheets) {
                        Text("Осталось ${output.remainingSheets ?: "—"} листов изделия")
                        OutlinedTextField(value = state.output?.sheets.orEmpty(), onValueChange = { onIntent(ManufacturingIntent.OutputSheetsChanged(it)) }, label = { Text("Фактически изготовлено листов") }, enabled = state.editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    } else OutlinedTextField(value = state.output?.quantity.orEmpty(), onValueChange = { onIntent(ManufacturingIntent.OutputQuantityChanged(it)) }, label = { Text("Фактически изготовлено, ${output.baseUnit.orEmpty()}") }, enabled = state.editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("manufacturing-output-qty"))
                }
            }
            if (state.prototype) {
                item {
                    Text("Плоский лист, затраченный на прототип:", style = MaterialTheme.typography.titleLarge)
                    Text("Другое сырьё выбрать нельзя. Готовые изделия в расход не предлагаются.")
                    OutlinedTextField(value = state.catalogQuery, onValueChange = { onIntent(ManufacturingIntent.CatalogQueryChanged(it)) }, label = { Text("Поиск плоского листа") }, enabled = state.editable, singleLine = true, modifier = Modifier.fillMaxWidth())
                    state.catalogError?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error) }
                }
                items(state.catalog, key = { "catalog-${it.productId}" }) { product ->
                    val dimensions = product.dimensions.filter { it.sheets > 0 && it.length.signum() > 0 }
                    if (dimensions.isEmpty()) {
                        OutlinedButton(onClick = { onIntent(ManufacturingIntent.AddMaterial(product.productId)) }, modifier = Modifier.testTag("prototype-add-${product.productId}"), enabled = state.editable && !state.materialSelecting) {
                            Text("Добавить ${product.productName ?: "материал"}: ${QuantityFormatter.format(product.quantity)} ${product.baseUnit}")
                        }
                    } else dimensions.forEach { dimension ->
                        OutlinedButton(onClick = { onIntent(ManufacturingIntent.AddMaterial(product.productId, dimension.length)) }, modifier = Modifier.testTag("prototype-add-${product.productId}-${dimension.length.toPlainString()}"), enabled = state.editable && !state.materialSelecting) {
                            Text("${product.productName ?: "Материал"} · ${QuantityFormatter.format(dimension.length)} м · ${dimension.sheets} листов")
                        }
                    }
                }
                if (state.catalogMore) item { TextButton(onClick = { onIntent(ManufacturingIntent.MoreCatalog) }, enabled = state.editable && !state.catalogLoading) { Text("Ещё материалы") } }
                items(state.materials, key = PrototypeMaterialRow::id) { row ->
                    PrototypeMaterialEditor(row, state.editable, { field, value -> onIntent(ManufacturingIntent.MaterialChanged(row.id, field, value)) }, { onIntent(ManufacturingIntent.RemoveMaterial(row.id)) })
                }
            }
            item {
                Button(onClick = { focus.clearFocus(); keyboard?.hide(); onIntent(ManufacturingIntent.PreviewClick) }, modifier = Modifier.fillMaxWidth().testTag("manufacturing-preview"), enabled = state.editable && !state.previewLoading && !state.materialSelecting && state.materials.none { it.loading || it.error != null }) { Text(if (state.previewLoading) "Рассчитываем…" else "Рассчитать расход") }
            }
            state.binding?.preview?.let { preview ->
                item { ManufacturingPreviewSummary(preview) }
                item { Button(onClick = { focus.clearFocus(); keyboard?.hide(); onIntent(ManufacturingIntent.ReviewClick) }, modifier = Modifier.fillMaxWidth().testTag("manufacturing-review"), enabled = state.editable && preview.canExecute && !state.previewLoading) { Text("Изготовить") } }
            }
        }
    }
    if (state.reviewOpen) state.binding?.preview?.let { preview ->
        AlertDialog(onDismissRequest = { onIntent(ManufacturingIntent.DismissReview) }, title = { Text(if (state.prototype) "Подтвердить первый прототип?" else "Подтвердить выпуск?") },
            text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ManufacturingPreviewSummary(preview)
                Text(if (state.prototype) "Первый выпуск входит в план" else "Материалы будут списаны, изделие добавится на склад")
            } },
            confirmButton = { Button(onClick = { onIntent(ManufacturingIntent.ConfirmClick) }, modifier = Modifier.testTag("manufacturing-confirm"), enabled = state.result != ExecutionResultState.Sending && state.pending == null) { Text("Подтвердить") } },
            dismissButton = { TextButton(onClick = { onIntent(ManufacturingIntent.DismissReview) }, enabled = state.result != ExecutionResultState.Sending) { Text("Назад") } })
    }
}
@Composable
private fun PrototypeMaterialEditor(row: PrototypeMaterialRow, enabled: Boolean, onChange: (PrototypeMaterialField, String) -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(row.product.name, style = MaterialTheme.typography.titleMedium)
        row.error?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error) }
        row.definition?.fullWidth?.let { Text("Ширина ${QuantityFormatter.format(it)} м") }
        if (!row.product.isFlatSheet()) Text("Этот товар не плоский лист и в расход не входит")
        if (row.product.sheet) {
            Text("Длина листа ${row.input.sheetLength.toBigDecimalOrNull()?.let(QuantityFormatter::format) ?: "—"} м")
            OutlinedTextField(value = row.input.sheets, onValueChange = { onChange(PrototypeMaterialField.SHEETS, it) }, label = { Text("Потрачено листов") }, enabled = enabled, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        }
        TextButton(onClick = onRemove, enabled = enabled) { Text("Убрать") }
    } }
}
@Composable
private fun ManufacturingPreviewSummary(preview: ManufacturingPreview) {
    Text("Будет изготовлено ${preview.output.productName}: ${preview.output.sheets?.let { "$it листов" } ?: "${QuantityFormatter.format(preview.output.quantity)} ${preview.output.baseUnit}"}", style = MaterialTheme.typography.titleMedium)
    preview.output.sheetLength?.let { Text("Длина ${QuantityFormatter.format(it)} м") }
    preview.materials.forEach { material ->
        Text("Будет списано ${material.productName}: ${material.sheets?.let { "$it листов" } ?: "${QuantityFormatter.format(material.quantity)} ${material.baseUnit}"}")
        material.sheetLength?.let { Text("Длина ${QuantityFormatter.format(it)} м") }
        if (material.dimensionAvailability != DimensionAvailability.NOT_APPLICABLE) Text("Свободно листов по размеру: ${material.freeSheets?.toString() ?: "неизвестно"}")
        if (material.missingQuantity.signum() > 0 || (material.missingSheets ?: 0) > 0) Text("Не хватает ${QuantityFormatter.format(material.missingQuantity)} ${material.baseUnit}${material.missingSheets?.let { " · $it листов" }.orEmpty()}", color = MaterialTheme.colorScheme.error)
    }
    preview.proposedRecipe?.let { Text("Этот расход запомним для следующих выпусков") }
    if (!preview.canExecute) Text("Выпуск пока невозможен", color = MaterialTheme.colorScheme.error)
    preview.issues.forEach { Text(it.message, color = if (preview.canExecute) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error) }
}
