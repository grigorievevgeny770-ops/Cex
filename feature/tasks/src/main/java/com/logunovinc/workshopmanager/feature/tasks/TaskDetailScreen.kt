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
fun TaskDetailRoute(viewModel: TaskDetailViewModel, onReceiveClick: (Long) -> Unit, onDispatchClick: (Long) -> Unit, onManufacturingClick: (Long) -> Unit, onFinishReceiveClick: (Long) -> Unit, onRecoveryClick: () -> Unit, onCompleted: (com.logunovinc.workshopmanager.domain.pending.CommandConfirmation) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompletedTaskEffect(state.lastConfirmation, onCompleted)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!viewModel.uiState.value.loading) viewModel.onIntent(TaskDetailIntent.Refresh)
            awaitCancellation()
        }
    }
    TaskDetailScreen(state, viewModel::onIntent, onReceiveClick, onDispatchClick, onManufacturingClick, onFinishReceiveClick, onRecoveryClick)
}
@Composable
fun TaskDetailScreen(state: TaskDetailUiState, onIntent: (TaskDetailIntent) -> Unit, onReceiveClick: (Long) -> Unit, onDispatchClick: (Long) -> Unit, onManufacturingClick: (Long) -> Unit, onFinishReceiveClick: (Long) -> Unit, onRecoveryClick: () -> Unit) {
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val task = state.task
    var showExecutions by remember(task?.id) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("task-detail-list").padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(task?.type?.title() ?: "Задание", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = { onIntent(TaskDetailIntent.Refresh) }, enabled = !state.closeSending) { Text("Обновить") }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.closeError?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error) }
            if (state.closeChecking) Text("Проверяем…")
            state.error?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error) }
            state.lastConfirmation?.let { Text("Готово", color = MaterialTheme.colorScheme.primary) }
            state.pending?.let { pending -> Text("Не подтверждено: ${pending.summary.operationalTitle()}"); TextButton(onClick = onRecoveryClick) { Text("Проверить действие") } }
            if (state.staleTask) Text("Обновите задание перед выполнением")
        }
        if (task != null) {
            item {
                if (task.comment.isNotBlank()) Text(task.comment)
                if (task.isClosedIncomplete) {
                    Text("Закрыта без полного выполнения", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    task.closedIncompleteReason?.let { Text("Причина: $it") }
                    Text("Выполненное сохранено. Оставшаяся работа закрыта")
                }
                if (task.isCompleted == true && task.outcome == "shortage") Text("Приёмка завершена с недостачей; менеджер разбирает расхождение")
                if (task.type == TaskType.MANUFACTURE_PRODUCT && task.production?.supported != true) Text("Нужна настройка производства. Обратитесь к руководителю")
                if (task.type == TaskType.UNKNOWN || task.isCompleted == null || task.isCancelled == null) Text("Выполнение недоступно")
                if (task.type == TaskType.MANUFACTURE_PRODUCT && task.canExecute) Button(onClick = { onManufacturingClick(task.id) }, modifier = Modifier.testTag("task-manufacturing"), enabled = state.executionAvailable) { Text("Изготовить изделие") }
                if (task.type == TaskType.DISPATCH_GOODS && task.canExecute) Button(onClick = { onDispatchClick(task.id) }, enabled = state.executionAvailable) { Text("Отправить товары") }
                if (task.canCloseIncomplete) OutlinedButton(onClick = { onIntent(TaskDetailIntent.CloseIncompleteClick) }, modifier = Modifier.testTag("task-close-incomplete"), enabled = state.closeAvailable) { Text("Завершить без полного выполнения") }
                if (task.type == TaskType.RECEIVE_GOODS && task.canExecute) Button(onClick = { onReceiveClick(task.id) }, modifier = Modifier.testTag("task-receive"), enabled = state.executionAvailable) { Text("Принять товары") }
                if (task.type == TaskType.RECEIVE_GOODS && task.canExecute) OutlinedButton(onClick = { onFinishReceiveClick(task.id) }, modifier = Modifier.testTag("task-finish-receive"), enabled = state.executionAvailable) { Text("Завершить приёмку") }
            }
            items(task.lines.filter { task.type != TaskType.MANUFACTURE_PRODUCT || it.role == "output" }, key = { "line-${it.id}" }) { line -> TaskLineCard(line) }
            if (state.executions.isNotEmpty() || state.executionsLoading || state.executionsError != null) item {
                TextButton(onClick = { showExecutions = !showExecutions }, modifier = Modifier.testTag("task-history-toggle")) { Text(if (showExecutions) "Скрыть историю" else "История") }
            }
            if (showExecutions) {
                item { state.executionsError?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error) }; if (state.executionsLoading) LinearProgressIndicator(Modifier.fillMaxWidth()) }
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
        Text("План: ${QuantityFormatter.format(line.planned)} ${line.baseUnit.orEmpty()}")
        Text("Факт: ${QuantityFormatter.format(line.actual)} · Осталось сделать: ${QuantityFormatter.format(line.remaining)} ${line.baseUnit.orEmpty()}")
        line.plannedUnit?.let { Text("В ${line.unitName.orEmpty()}: план ${QuantityFormatter.format(it)}, факт ${line.actualUnit?.let(QuantityFormatter::format) ?: "—"}, осталось сделать ${line.remainingUnit?.let(QuantityFormatter::format) ?: "—"}") }
        line.plannedSheets?.let { Text("Листы: план $it, факт ${line.actualSheets ?: "—"}, осталось сделать ${line.remainingSheets ?: "—"}") }
        line.sheetLength?.let { Text("Длина ${QuantityFormatter.format(it)} м") }
    } }

}
