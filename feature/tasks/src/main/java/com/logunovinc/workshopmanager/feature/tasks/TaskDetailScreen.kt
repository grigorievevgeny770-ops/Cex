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
fun TaskDetailRoute(viewModel: TaskDetailViewModel, onCompleted: (com.logunovinc.workshopmanager.domain.pending.CommandConfirmation) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompletedTaskEffect(state.lastConfirmation, onCompleted)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!viewModel.uiState.value.loading) viewModel.onIntent(TaskDetailIntent.Refresh)
            awaitCancellation()
        }
    }
    TaskDetailScreen(state, viewModel::onIntent)
}
@Composable
fun TaskDetailScreen(state: TaskDetailUiState, onIntent: (TaskDetailIntent) -> Unit) {
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val task = state.task
    var showExecutions by remember(task?.id) { mutableStateOf(false) }
    RefreshableContent(state.refreshing, state.canRefresh, { onIntent(TaskDetailIntent.PullRefresh) }, automaticLoading = state.loading || state.closeChecking || state.closeSending || state.retrying || state.executionsLoading || state.historyLoading) {
        LazyColumn(Modifier.fillMaxSize().testTag("task-detail-list").padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (task?.type == TaskType.MANUFACTURE_PRODUCT) "Заявка" else "Документ склада", style = MaterialTheme.typography.headlineSmall)
                }
                state.retryError?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error) }
                state.closeError?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error) }
                if (state.closeChecking) Text("Проверяем…")
                state.error?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error) }
                state.lastConfirmation?.let { Text("Готово", color = MaterialTheme.colorScheme.primary) }
                state.pending?.let { pending ->
                    Text(pending.summary.operationalTitle(), style = MaterialTheme.typography.titleMedium)
                    pending.summary.operationalLines().forEach { Text(it) }
                    if (pending.summary.comment.isNotBlank()) Text(pending.summary.comment)
                    Text(when (pending.state) {
                        com.logunovinc.workshopmanager.domain.pending.PendingState.PREPARED -> "Сохранено перед отправкой"
                        com.logunovinc.workshopmanager.domain.pending.PendingState.NEEDS_REVIEW -> "Требуется сверка. Не выполняйте заново"
                        else -> "Исход неизвестен. Возможно, действие уже выполнено"
                    })
                    if (state.retryError == null) pending.error?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { onIntent(TaskDetailIntent.RetryPendingClick) }, enabled = !state.retrying && !state.closeSending,
                        modifier = Modifier.testTag("task-retry-pending")) { Text(if (state.retrying) "Отправляем…" else "Повторить действие") }
                }
                if (state.staleTask) Text("Обновите задание перед выполнением")
            }
            if (task != null) {
                item {
                    Text(task.workshopStage().title(), style = MaterialTheme.typography.titleMedium)
                    Text(task.dueAt?.let { "Нужно к ${localDay(it)}" } ?: "Дата не указана")
                    if (task.comment.isNotBlank()) Text(task.comment)
                    if (task.isClosedIncomplete) {
                        task.closedIncompleteReason?.let { Text("Причина: $it") }
                        Text("Выполненное на сервере сохранено. Оставшаяся работа закрыта там же")
                    }
                    if (task.type != TaskType.MANUFACTURE_PRODUCT) Text("Это не заявка на изделия.")
                    else if (task.productRequestLines().isEmpty()) Text("В заявке нет изделий.")
                    when (task.workshopStage()) {
                        WorkshopRequestStage.NEW, WorkshopRequestStage.IN_PROGRESS -> Text("Отметить «В работе», «Изготовлено» или «Передано на склад» пока нельзя: сервер такой шаг не принимает.")
                        WorkshopRequestStage.SERVER_COMPLETED -> Text("Сервер завершил документ. Отдельных статусов «Изготовлено» и «Передано на склад» он не присылает.")
                        WorkshopRequestStage.UNSUPPORTED -> Text("Статус заявки сервер не подтвердил. Действие недоступно.")
                        else -> Unit
                    }
                }
                items(task.productRequestLines(), key = { "line-${it.id}" }) { line ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { RequestLineText(line) } }
                }
                if (state.executions.isNotEmpty() || state.executionsLoading || state.executionsError != null) item {
                    TextButton(onClick = { showExecutions = !showExecutions }, modifier = Modifier.testTag("task-history-toggle")) { Text(if (showExecutions) "Скрыть историю" else "История") }
                }
                if (showExecutions) {
                    item { state.executionsError?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error) } }
                    items(state.executions, key = { "execution-${it.id}" }) { execution ->
                        Card(Modifier.fillMaxWidth().testTag("execution-${execution.id}")) { Column(Modifier.padding(12.dp)) {
                            Text(localDate(execution.at))
                            if (execution.final) Text("Окончательная приёмка")
                            execution.lines.forEach { actual ->
                                val frozen = task.lines.firstOrNull { it.id == actual.taskLineId && it.productId == actual.productId }
                                val productName = frozen?.productName ?: when (actual.role) {
                                    "input" -> "Материал"
                                    "output" -> "Изделие"
                                    else -> "Товар"
                                }
                                Text("$productName: ${QuantityFormatter.format(actual.actual)} ${frozen?.baseUnit.orEmpty()}${actual.sheets?.let { count -> " · $count листов" }.orEmpty()}")
                                frozen?.sheetLength?.let { Text("Длина ${QuantityFormatter.format(it)} м") }
                            }
                            if (execution.comment.isNotBlank()) Text(execution.comment)
                        } }
                    }
                    if (state.executionsMore) item { OutlinedButton(onClick = { onIntent(TaskDetailIntent.LoadMoreExecutions) }, enabled = !state.executionsLoading) { Text("Ещё факты") } }
                }

            }
        }

    }
    state.closeDialog?.let { dialog ->
        AlertDialog(onDismissRequest = { if (!state.closeSending) onIntent(TaskDetailIntent.DismissClose) },
            title = { Text("Завершить без полного выполнения?") },
            text = { Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Оставшаяся работа будет закрыта. Выполненное сохранится. Это не списание потери")
                dialog.task.lines.filter { dialog.task.type != TaskType.MANUFACTURE_PRODUCT || it.role == "output" }.forEach { line ->
                    Text(line.productName)
                    Text("План ${QuantityFormatter.format(line.planned)} · факт ${QuantityFormatter.format(line.actual)} · остаток ${QuantityFormatter.format(line.remaining)} ${line.baseUnit.orEmpty()}")
                    line.remainingSheets?.let { Text("Листы: план ${line.plannedSheets ?: "—"} · факт ${line.actualSheets ?: "—"} · остаток $it") }
                    line.sheetLength?.let { Text("Длина ${QuantityFormatter.format(it)} м") }
                }
                OutlinedTextField(value = dialog.reason, onValueChange = { onIntent(TaskDetailIntent.CloseReasonChanged(it)) },
                    label = { Text("Причина закрытия") }, enabled = !state.closeSending, modifier = Modifier.fillMaxWidth().testTag("close-incomplete-reason"))
                state.closeError?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { Button(onClick = { focus.clearFocus();keyboard?.hide();onIntent(TaskDetailIntent.ConfirmClose) }, enabled = !state.closeSending && state.pending == null,
                modifier = Modifier.testTag("close-incomplete-confirm")) { Text(if (state.closeSending) "Закрываем…" else "Закрыть задачу") } },
            dismissButton = { TextButton(onClick = { onIntent(TaskDetailIntent.DismissClose) }, enabled = !state.closeSending) { Text("Назад") } })
    }

}
