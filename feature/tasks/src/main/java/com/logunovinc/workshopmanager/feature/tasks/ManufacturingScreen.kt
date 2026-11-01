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
import com.logunovinc.workshopmanager.domain.production.*

@Composable
fun ManufacturingRoute(viewModel: ManufacturingViewModel, onRecoveryClick: () -> Unit, onDetailClick: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ManufacturingScreen(state, viewModel::onIntent, onRecoveryClick, onDetailClick)
}
@Composable
fun ManufacturingScreen(state: ManufacturingUiState, onIntent: (ManufacturingIntent) -> Unit, onRecoveryClick: () -> Unit, onDetailClick: () -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val task = state.task
    val output = task?.lines?.filter { it.role == "output" }?.singleOrNull()
    LazyColumn(Modifier.fillMaxSize().testTag("manufacturing-list").padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Изготовление · №${task?.id ?: ""}", style = MaterialTheme.typography.headlineMedium)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            when (val result = state.result) {
                is ExecutionResultState.Confirmed -> { Text("Действие подтверждено сервером · версия ${result.receipt.taskVersion}", color = MaterialTheme.colorScheme.primary); TextButton(onClick = onDetailClick) { Text("Карточка задания") } }
                is ExecutionResultState.Unresolved -> { Text("Исход неизвестен: ${result.error.message}", color = MaterialTheme.colorScheme.error); TextButton(onClick = onRecoveryClick) { Text("Восстановить исходный запрос") } }
                is ExecutionResultState.Rejected -> Text(result.error.message, color = MaterialTheme.colorScheme.error)
                ExecutionResultState.Sending -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> Unit
            }
            state.pending?.let { pending -> Text("Сохранённый запрос: ${pending.summary.title}"); pending.summary.lines.forEach { Text(it) }; TextButton(onClick = onRecoveryClick) { Text("Открыть журнал запросов") } }
            if (task?.production?.supported != true || output == null) Text("Для выпуска нужна поддерживаемая норма сервера v2 и однозначная строка изделия. Старый ручной режим не используется")
            if (output != null) {
                Text(output.productName, style = MaterialTheme.typography.titleLarge)
                Text("План ${QuantityFormatter.format(output.planned)} · изготовлено ${QuantityFormatter.format(output.actual)} · осталось ${QuantityFormatter.format(output.remaining)} ${output.baseUnit.orEmpty()}")
                if (state.prototype) Text("Первый прототип входит в план. Укажите его выпуск и реальные материалы один раз; после успешного подтверждения сервер закрепит норму только для этого задания")
                else { Text("Введите только выпуск. Расход рассчитает сервер по норме"); task.production?.let { RecipeSummary(it) } }
                val sheets = output.plannedSheets != null || output.remainingSheets != null
                if (sheets) {
                    Text("Осталось ${output.remainingSheets ?: "—"} листов изделия")
                    OutlinedTextField(value = state.output?.sheets.orEmpty(), onValueChange = { onIntent(ManufacturingIntent.OutputSheetsChanged(it)) }, label = { Text("Фактически изготовлено листов") }, enabled = state.editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                } else OutlinedTextField(value = state.output?.quantity.orEmpty(), onValueChange = { onIntent(ManufacturingIntent.OutputQuantityChanged(it)) }, label = { Text("Фактически изготовлено, ${output.baseUnit.orEmpty()}") }, enabled = state.editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("manufacturing-output-qty"))
            }
        }
        if (state.prototype) {
            item {
                Text("Материалы первого выпуска", style = MaterialTheme.typography.titleLarge)
                Text("Выбирайте товары из каталога. Каталог не подтверждает их наличие в цехе; доступность проверит расчёт сервера")
                OutlinedTextField(value = state.catalogQuery, onValueChange = { onIntent(ManufacturingIntent.CatalogQueryChanged(it)) }, label = { Text("Поиск материала") }, enabled = state.editable, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { onIntent(ManufacturingIntent.SearchCatalog) }, enabled = state.editable && !state.catalogLoading) { Text("Найти материалы") }
                if (state.catalogLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.catalogError?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            }
            items(state.catalog, key = { "catalog-${it.id}" }) { product ->
                OutlinedButton(onClick = { onIntent(ManufacturingIntent.AddMaterial(product)) }, modifier = Modifier.testTag("prototype-add-${product.id}"), enabled = state.editable) { Text("Добавить ${product.name}${product.sku?.let { " · $it" }.orEmpty()}") }
            }
            if (state.catalogMore) item { TextButton(onClick = { onIntent(ManufacturingIntent.MoreCatalog) }, enabled = state.editable && !state.catalogLoading) { Text("Ещё товары каталога") } }
            items(state.materials, key = PrototypeMaterialRow::id) { row ->
                PrototypeMaterialEditor(row, state.editable, { field, value -> onIntent(ManufacturingIntent.MaterialChanged(row.id, field, value)) }, { onIntent(ManufacturingIntent.RemoveMaterial(row.id)) })
            }
        }
        item {
            OutlinedTextField(value = state.comment, onValueChange = { onIntent(ManufacturingIntent.CommentChanged(it)) }, label = { Text("Комментарий") }, enabled = state.editable, modifier = Modifier.fillMaxWidth())
            Button(onClick = { focus.clearFocus(); keyboard?.hide(); onIntent(ManufacturingIntent.PreviewClick) }, modifier = Modifier.fillMaxWidth().testTag("manufacturing-preview"), enabled = state.editable && !state.previewLoading) { Text(if (state.previewLoading) "Рассчитываем…" else "Рассчитать расход на сервере") }
            if (state.previewLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        state.binding?.preview?.let { preview ->
            item { ManufacturingPreviewSummary(preview) }
            item { Button(onClick = { focus.clearFocus(); keyboard?.hide(); onIntent(ManufacturingIntent.ReviewClick) }, modifier = Modifier.fillMaxWidth().testTag("manufacturing-review"), enabled = state.editable && preview.canExecute && !state.previewLoading) { Text("Проверить и подтвердить выпуск") } }
        }
        item { TextButton(onClick = { onIntent(ManufacturingIntent.Refresh) }, enabled = state.result != ExecutionResultState.Sending) { Text("Обновить задание и норму") } }
    }
    if (state.reviewOpen) state.binding?.preview?.let { preview ->
        AlertDialog(onDismissRequest = { onIntent(ManufacturingIntent.DismissReview) }, title = { Text(if (state.prototype) "Подтвердить первый прототип?" else "Подтвердить выпуск?") },
            text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ManufacturingPreviewSummary(preview)
                Text(if (state.prototype) "Материалы, изделие и норма будут сохранены сервером одним действием. Прототип входит в план" else "Сервер заново проверит норму и доступность перед списанием и выпуском")
                if (state.comment.isNotBlank()) Text(state.comment)
            } },
            confirmButton = { Button(onClick = { onIntent(ManufacturingIntent.ConfirmClick) }, modifier = Modifier.testTag("manufacturing-confirm"), enabled = state.result != ExecutionResultState.Sending && state.pending == null) { Text("Подтвердить") } },
            dismissButton = { TextButton(onClick = { onIntent(ManufacturingIntent.DismissReview) }, enabled = state.result != ExecutionResultState.Sending) { Text("Назад") } })
    }
}
@Composable
private fun PrototypeMaterialEditor(row: PrototypeMaterialRow, enabled: Boolean, onChange: (PrototypeMaterialField, String) -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(row.product.name, style = MaterialTheme.typography.titleMedium)
        if (row.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        row.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        row.definition?.fullWidth?.let { Text("Текущая ширина ${QuantityFormatter.format(it)} м; геометрию нового материала проверит сервер") }
        OutlinedTextField(value = row.input.quantity, onValueChange = { onChange(PrototypeMaterialField.QUANTITY, it) }, label = { Text("Базовый расход, ${row.product.baseUnit}${if (row.product.sheet) " (можно пропустить при листах)" else ""}") }, enabled = enabled, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("prototype-material-qty-${row.id}"))
        if (row.product.sheet) {
            OutlinedTextField(value = row.input.sheets, onValueChange = { onChange(PrototypeMaterialField.SHEETS, it) }, label = { Text("Листов материала, если расход по листам") }, enabled = enabled, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = row.input.sheetLength, onValueChange = { onChange(PrototypeMaterialField.LENGTH, it) }, label = { Text("Длина листа материала, м") }, enabled = enabled, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        }
        row.definition?.units?.takeIf { it.isNotEmpty() }?.let { Text("Единицы товара: ${it.joinToString { unit -> unit.name }}") }
        OutlinedTextField(value = row.input.unitName, onValueChange = { onChange(PrototypeMaterialField.UNIT_NAME, it) }, label = { Text("Единица расхода (необязательно)") }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = row.input.unitQuantity, onValueChange = { onChange(PrototypeMaterialField.UNIT_QUANTITY, it) }, label = { Text("Количество в этой единице") }, enabled = enabled, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onRemove, enabled = enabled) { Text("Убрать материал из формы") }
    } }
}
@Composable
fun RecipeSummary(recipe: ProductionDescriptor) {
    val basis = recipe.basisOutput
    Text("Норма задания · версия ${recipe.recipeVersion}", style = MaterialTheme.typography.titleMedium)
    if (basis != null) Text("На ${basis.sheets?.let { "$it листов" } ?: "${QuantityFormatter.format(basis.quantity)} ${basis.baseUnit}"}")
    recipe.materials.forEach { material -> Text("${material.productName}: ${material.sheets?.let { "$it листов" } ?: "${QuantityFormatter.format(material.quantity)} ${material.baseUnit}"}") }
}
@Composable
private fun ManufacturingPreviewSummary(preview: ManufacturingPreview) {
    Text("Будет изготовлено ${preview.output.productName}: ${preview.output.sheets?.let { "$it листов" } ?: "${QuantityFormatter.format(preview.output.quantity)} ${preview.output.baseUnit}"}", style = MaterialTheme.typography.titleMedium)
    preview.materials.forEach { material ->
        Text("Будет списано ${material.productName}: ${material.sheets?.let { "$it листов" } ?: "${QuantityFormatter.format(material.quantity)} ${material.baseUnit}"}")
        Text("В цехе ${QuantityFormatter.format(material.stockQuantity)} · резерв ${QuantityFormatter.format(material.reservedQuantity)} · свободно ${QuantityFormatter.format(material.freeQuantity)} ${material.baseUnit}")
        if (material.dimensionAvailability != DimensionAvailability.NOT_APPLICABLE) Text("Свободно листов по размеру: ${material.freeSheets?.toString() ?: "неизвестно"}")
        if (material.missingQuantity.signum() > 0 || (material.missingSheets ?: 0) > 0) Text("Не хватает ${QuantityFormatter.format(material.missingQuantity)} ${material.baseUnit}${material.missingSheets?.let { " · $it листов" }.orEmpty()}", color = MaterialTheme.colorScheme.error)
    }
    preview.proposedRecipe?.let { Text("Предлагаемая норма — пока не сохранена. Появится только после успешного подтверждения"); RecipeSummary(it) }
    if (!preview.canExecute) Text("Выпуск пока невозможен", color = MaterialTheme.colorScheme.error)
    preview.issues.forEach { Text("${it.code}: ${it.message}", color = if (preview.canExecute) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error) }
}
