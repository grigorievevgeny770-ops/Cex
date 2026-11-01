package com.logunovinc.workshopmanager.feature.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    val task = state.task
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(task?.let { "Задание №${it.id}" } ?: "Задание", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = { onIntent(TaskDetailIntent.Refresh) }, enabled = state.submission != SubmissionState.Sending) { Text("Обновить") }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            when (val submission = state.submission) {
                is SubmissionState.Confirmed -> Text("Подтверждено сервером · версия ${submission.receipt.taskVersion}", color = MaterialTheme.colorScheme.primary)
                is SubmissionState.Unresolved -> {
                    Text("Исход запроса неизвестен: ${submission.error.message}", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRecoveryClick) { Text("Восстановить исходный запрос") }
                }
                is SubmissionState.Rejected -> Text(submission.error.message, color = MaterialTheme.colorScheme.error)
                SubmissionState.Sending -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> Unit
            }
            state.pending?.let { pending -> Text("Сохранённый запрос: ${pending.summary.title}"); TextButton(onClick = onRecoveryClick) { Text("Открыть журнал запросов") } }
            if (state.staleTask) Text("Для следующих действий нужно получить актуальную карточку")
        }
        if (task != null) {
            item {
                Text(task.type.title(), style = MaterialTheme.typography.titleLarge)
                Text(task.status.title())
                Text(task.assigneeId?.let { "Исполнитель №$it" } ?: "Не назначено")
                Text("Создано ${localDate(task.createdAt)} · срок ${localDate(task.dueAt)}")
                task.startedAt?.let { Text("Начато ${localDate(it)}") }
                task.completedAt?.let { Text("Завершено ${localDate(it)}") }
                if (task.comment.isNotBlank()) Text(task.comment)
                if (task.status == TaskStatus.COMPLETED && task.outcome == "shortage") Text("Приёмка завершена с недостачей; менеджер разбирает расхождение")
                if (task.type == TaskType.MANUFACTURE_PRODUCT && task.production?.supported != true) Text("Производственный режим требует серверной нормы v2. Старое ручное списание не поддерживается; обратитесь к менеджеру")
                if (task.type == TaskType.UNKNOWN || task.status == TaskStatus.UNKNOWN) Text("Неподдерживаемый тип или статус сервера. Изменения недоступны")
                if (task.type == TaskType.MANUFACTURE_PRODUCT && task.production?.supported == true) task.production?.let { RecipeSummary(it) }
                if (task.type == TaskType.MANUFACTURE_PRODUCT && task.canExecute) Button(onClick = { onManufacturingClick(task.id) }, enabled = state.pending == null && !state.loading && !state.staleTask && state.submission != SubmissionState.Sending && state.submission !is SubmissionState.Unresolved) { Text("Изготовить изделие") }
                if (task.type == TaskType.DISPATCH_GOODS && task.canExecute) Button(onClick = { onDispatchClick(task.id) }, enabled = state.pending == null && !state.loading && !state.staleTask && state.submission != SubmissionState.Sending && state.submission !is SubmissionState.Unresolved) { Text("Отправить товары") }
                if (task.type == TaskType.RECEIVE_GOODS && task.canExecute) Button(onClick = { onReceiveClick(task.id) }, enabled = state.pending == null && !state.loading && !state.staleTask && state.submission != SubmissionState.Sending && state.submission !is SubmissionState.Unresolved) { Text("Принять товары") }
                if (task.canStart) Button(onClick = { onIntent(TaskDetailIntent.StartClick) }, enabled = state.pending == null && !state.loading && !state.staleTask && state.submission != SubmissionState.Sending && state.submission !is SubmissionState.Unresolved) { Text("Взять в работу") }
            }
            items(task.lines, key = { "line-${it.id}" }) { line -> TaskLineCard(line) }
            items(task.discrepancies, key = { "difference-${it.id}" }) { discrepancy ->
                Text("Расхождение №${discrepancy.id}: недостача ${QuantityFormatter.format(discrepancy.missing)}, не разрешено ${QuantityFormatter.format(discrepancy.unresolved)}. ${discrepancy.reason}")
            }
            item { Text("Факты выполнения", style = MaterialTheme.typography.titleLarge); state.executionsError?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }; if (state.executionsLoading) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(state.executions, key = { "execution-${it.id}" }) { execution ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text("Факт №${execution.id} · ${localDate(execution.at)}")
                    Text("Работник №${execution.actorId}${if (execution.final) " · окончательная приёмка" else ""}")
                    execution.lines.forEach { Text("Строка №${it.taskLineId}: ${QuantityFormatter.format(it.actual)}${it.sheets?.let { count -> " · $count листов" }.orEmpty()}") }
                    if (execution.comment.isNotBlank()) Text(execution.comment)
                    execution.receiptId?.let { Text("Квитанция приёмки №$it") }
                    execution.transferId?.let { Text("Перемещение №$it") }
                    execution.inventoryDocumentId?.let { Text("Документ производства №$it") }
                } }
            }
            if (state.executionsMore) item { OutlinedButton(onClick = { onIntent(TaskDetailIntent.LoadMoreExecutions) }, enabled = !state.executionsLoading) { Text("Ещё факты") } }
            item { Text("История действий", style = MaterialTheme.typography.titleLarge); state.historyError?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }; if (state.historyLoading) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(state.history, key = { "history-${it.id}" }) { event ->
                Column { Text("№${event.id} · ${event.eventType} · ${localDate(event.at)}"); event.actorId?.let { Text("Работник №$it") }; event.details.forEach { Text("${it.first}: ${it.second}") } }
            }
            if (state.historyMore) item { OutlinedButton(onClick = { onIntent(TaskDetailIntent.LoadMoreHistory) }, enabled = !state.historyLoading) { Text("Ещё историю") } }
        }
    }
    if (state.submission == SubmissionState.ConfirmStart && task != null) AlertDialog(
        onDismissRequest = { onIntent(TaskDetailIntent.DismissConfirmation) }, title = { Text("Взять задание №${task.id}?") },
        text = { Text("Задание будет закреплено за вами. Сервер проверит доступность и версию ${task.version}") },
        confirmButton = { Button(onClick = { onIntent(TaskDetailIntent.ConfirmStart) }, enabled = !state.loading && !state.staleTask) { Text("Подтвердить") } },
        dismissButton = { TextButton(onClick = { onIntent(TaskDetailIntent.DismissConfirmation) }) { Text("Назад") } })
}
@Composable
private fun TaskLineCard(line: TaskLine) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(line.productName, style = MaterialTheme.typography.titleMedium)
        line.sku?.let { Text("Артикул $it") }
        Text("Строка №${line.id} · ${line.role}")
        Text("План: ${QuantityFormatter.format(line.planned)} ${line.baseUnit.orEmpty()}")
        Text("Факт: ${QuantityFormatter.format(line.actual)} · Осталось: ${QuantityFormatter.format(line.remaining)} ${line.baseUnit.orEmpty()}")
        line.plannedUnit?.let { Text("В ${line.unitName.orEmpty()}: план ${QuantityFormatter.format(it)}, факт ${line.actualUnit?.let(QuantityFormatter::format) ?: "—"}, осталось ${line.remainingUnit?.let(QuantityFormatter::format) ?: "—"}") }
        line.plannedSheets?.let { Text("Листы: план $it, факт ${line.actualSheets ?: "—"}, осталось ${line.remainingSheets ?: "—"}") }
        line.sheetLength?.let { Text("Длина ${QuantityFormatter.format(it)} м") }
    } }
}
