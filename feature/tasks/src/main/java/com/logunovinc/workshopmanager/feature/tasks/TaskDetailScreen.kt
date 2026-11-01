package com.logunovinc.workshopmanager.feature.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import com.logunovinc.workshopmanager.core.ui.*
import com.logunovinc.workshopmanager.domain.task.*

@Composable
fun TaskDetailRoute(viewModel: TaskDetailViewModel, onReceiveClick: (Long) -> Unit, onDispatchClick: (Long) -> Unit, onManufacturingClick: (Long) -> Unit, onRecoveryClick: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!viewModel.uiState.value.loading) viewModel.onIntent(TaskDetailIntent.Refresh)
            awaitCancellation()
        }
    }
    TaskDetailScreen(state, viewModel::onIntent, onReceiveClick, onDispatchClick, onManufacturingClick, onRecoveryClick)
}
@Composable
fun TaskDetailScreen(state: TaskDetailUiState, onIntent: (TaskDetailIntent) -> Unit, onReceiveClick: (Long) -> Unit, onDispatchClick: (Long) -> Unit, onManufacturingClick: (Long) -> Unit, onRecoveryClick: () -> Unit) {
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val task = state.task
    LazyColumn(Modifier.fillMaxSize().testTag("task-detail-list").padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(task?.let { "Задание №${it.id}" } ?: "Задание", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = { onIntent(TaskDetailIntent.Refresh) }, enabled = !state.closeSending) { Text("Обновить") }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.closeError?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            if (state.closeChecking) Text("Проверяем поддержку закрытия на выбранном сервере…")
            state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            state.lastConfirmation?.let { Text("Подтверждено сервером · версия ${it.taskVersion}", color = MaterialTheme.colorScheme.primary) }
            state.pending?.let { pending -> Text("Сохранённый запрос: ${pending.summary.title}"); TextButton(onClick = onRecoveryClick) { Text("Открыть журнал запросов") } }
            if (state.staleTask) Text("Для следующих действий нужно получить актуальную карточку")
        }
        if (task != null) {
            item {
                Text(task.type.title(), style = MaterialTheme.typography.titleLarge)
                Text("Создано ${localDate(task.createdAt)} · срок ${localDate(task.dueAt)}")
                if (task.comment.isNotBlank()) Text(task.comment)
                if (task.isClosedIncomplete) {
                    Text("Закрыта без полного выполнения", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    task.closedIncompleteReason?.let { Text("Причина: $it") }
                    task.closedIncompleteAt?.let { Text("Закрыто ${localDate(it)}") }
                    Text("Остаток больше недоступен к выполнению. Ранее отправленные товары и изготовленные изделия сохранены")
                }
                if (task.isCompleted == true && task.outcome == "shortage") Text("Приёмка завершена с недостачей; менеджер разбирает расхождение")
                if (task.type == TaskType.MANUFACTURE_PRODUCT && task.production?.supported != true) Text("Производственный режим требует серверной нормы v2. Старое ручное списание не поддерживается; обратитесь к менеджеру")
                if (task.type == TaskType.UNKNOWN || task.isCompleted == null || task.isCancelled == null) Text("Сервер не разрешает выполнение этого задания")
                if (task.type == TaskType.MANUFACTURE_PRODUCT && task.production?.supported == true) task.production?.let {
                    if (it.status == com.logunovinc.workshopmanager.domain.production.RecipeStatus.AWAITING_PROTOTYPE) Text("Требуется первый прототип; норма ещё не задана") else RecipeSummary(it)
                }
                if (task.type == TaskType.MANUFACTURE_PRODUCT && task.canExecute) Button(onClick = { onManufacturingClick(task.id) }, modifier = Modifier.testTag("task-manufacturing"), enabled = state.executionAvailable) { Text("Изготовить изделие") }
                if (task.type == TaskType.DISPATCH_GOODS && task.canExecute) Button(onClick = { onDispatchClick(task.id) }, enabled = state.executionAvailable) { Text("Отправить товары") }
                if (task.canCloseIncomplete) OutlinedButton(onClick = { onIntent(TaskDetailIntent.CloseIncompleteClick) }, modifier = Modifier.testTag("task-close-incomplete"), enabled = state.closeAvailable) { Text("Завершить без полного выполнения") }
                if (task.type == TaskType.RECEIVE_GOODS && task.canExecute) Button(onClick = { onReceiveClick(task.id) }, modifier = Modifier.testTag("task-receive"), enabled = state.executionAvailable) { Text("Принять товары") }
            }
            items(task.lines, key = { "line-${it.id}" }) { line -> TaskLineCard(line) }
            items(task.discrepancies, key = { "difference-${it.id}" }) { discrepancy ->
                Text("Расхождение №${discrepancy.id}: недостача ${QuantityFormatter.format(discrepancy.missing)}, не разрешено ${QuantityFormatter.format(discrepancy.unresolved)}. ${discrepancy.reason}")
            }
            item { Text("Факты выполнения", style = MaterialTheme.typography.titleLarge); state.executionsError?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }; if (state.executionsLoading) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(state.executions, key = { "execution-${it.id}" }) { execution ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text("Факт №${execution.id} · ${localDate(execution.at)}")
                    if (execution.final) Text("Окончательная приёмка")
                    execution.lines.forEach { actual ->
                        val productName = task.lines.firstOrNull { it.id == actual.taskLineId && it.productId == actual.productId }?.productName ?: when (actual.role) {
                            "input" -> "Материал"
                            "output" -> "Изделие"
                            else -> "Товар"
                        }
                        Text("$productName: ${QuantityFormatter.format(actual.actual)}${actual.sheets?.let { count -> " · $count листов" }.orEmpty()}")
                    }
                    if (execution.comment.isNotBlank()) Text(execution.comment)
                    execution.receiptId?.let { Text("Квитанция приёмки №$it") }
                    execution.transferId?.let { Text("Перемещение №$it") }
                    execution.inventoryDocumentId?.let { Text("Документ производства №$it") }
                } }
            }
            if (state.executionsMore) item { OutlinedButton(onClick = { onIntent(TaskDetailIntent.LoadMoreExecutions) }, enabled = !state.executionsLoading) { Text("Ещё факты") } }
            if (state.debugHistoryAvailable) {
                item { TextButton(onClick = { onIntent(TaskDetailIntent.ToggleDebugHistory) }) {
                    Text(if (state.debugHistoryExpanded) "Скрыть отладочную историю" else "Отладочная история")
                } }
                if (state.debugHistoryExpanded) {
                    item { Text("История действий", style = MaterialTheme.typography.titleLarge); state.historyError?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }; if (state.historyLoading) LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    items(state.history, key = { "history-${it.id}" }) { event ->
                        Column { Text("№${event.id} · ${event.eventType} · ${localDate(event.at)}"); event.details.filterNot { it.first == "Исполнитель" }.forEach { Text("${it.first}: ${it.second}") } }
                    }
                    if (state.historyMore) item { OutlinedButton(onClick = { onIntent(TaskDetailIntent.LoadMoreHistory) }, enabled = !state.historyLoading) { Text("Ещё историю") } }
                }
            }
        }
    }

    state.closeDialog?.let { dialog ->
        AlertDialog(onDismissRequest = { if (!state.closeSending) onIntent(TaskDetailIntent.DismissClose) },
            title = { Text("Завершить без полного выполнения?") },
            text = { Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Оставшаяся работа больше не будет доступна к выполнению. Ранее отправленные товары и изготовленные изделия сохранятся. План, выполненное количество и невыполненный остаток будут видны в истории заданий. Закрытие не создаёт складских движений и не утверждает потерю")
                dialog.task.lines.forEach { line ->
                    Text(line.productName)
                    Text("План ${QuantityFormatter.format(line.planned)} · факт ${QuantityFormatter.format(line.actual)} · остаток ${QuantityFormatter.format(line.remaining)} ${line.baseUnit.orEmpty()}")
                    line.remainingSheets?.let { Text("Листы: план ${line.plannedSheets ?: "—"} · факт ${line.actualSheets ?: "—"} · остаток $it") }
                }
                OutlinedTextField(value = dialog.reason, onValueChange = { onIntent(TaskDetailIntent.CloseReasonChanged(it)) },
                    label = { Text("Причина закрытия") }, enabled = !state.closeSending, modifier = Modifier.fillMaxWidth().testTag("close-incomplete-reason"))
                state.closeError?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
                if (state.closeSending) LinearProgressIndicator(Modifier.fillMaxWidth())
            } },
            confirmButton = { Button(onClick = { focus.clearFocus();keyboard?.hide();onIntent(TaskDetailIntent.ConfirmClose) }, enabled = !state.closeSending && state.pending == null,
                modifier = Modifier.testTag("close-incomplete-confirm")) { Text(if (state.closeSending) "Закрываем…" else "Закрыть задачу") } },
            dismissButton = { TextButton(onClick = { onIntent(TaskDetailIntent.DismissClose) }, enabled = !state.closeSending) { Text("Назад") } })
    }

}
@Composable
private fun TaskLineCard(line: TaskLine) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(line.productName, style = MaterialTheme.typography.titleMedium)
        line.sku?.let { Text("Артикул $it") }
        Text("План: ${QuantityFormatter.format(line.planned)} ${line.baseUnit.orEmpty()}")
        Text("Факт: ${QuantityFormatter.format(line.actual)} · Осталось: ${QuantityFormatter.format(line.remaining)} ${line.baseUnit.orEmpty()}")
        line.plannedUnit?.let { Text("В ${line.unitName.orEmpty()}: план ${QuantityFormatter.format(it)}, факт ${line.actualUnit?.let(QuantityFormatter::format) ?: "—"}, осталось ${line.remainingUnit?.let(QuantityFormatter::format) ?: "—"}") }
        line.plannedSheets?.let { Text("Листы: план $it, факт ${line.actualSheets ?: "—"}, осталось ${line.remainingSheets ?: "—"}") }
        line.sheetLength?.let { Text("Длина ${QuantityFormatter.format(it)} м") }
    } }

}
