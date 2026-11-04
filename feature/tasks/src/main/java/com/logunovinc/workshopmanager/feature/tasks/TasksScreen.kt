package com.logunovinc.workshopmanager.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.logunovinc.workshopmanager.core.ui.*
import com.logunovinc.workshopmanager.domain.task.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun TaskListRoute(viewModel: TaskListViewModel, onTaskClick: (Long) -> Unit, onRecoveryClick: () -> Unit, consumeCompletion: () -> String? = { null }) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val completion by rememberUpdatedState(consumeCompletion)
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val commandId = completion()
            if (commandId != null) viewModel.onIntent(TaskListIntent.Completed(commandId)) else viewModel.onIntent(TaskListIntent.Poll)
            while (isActive) { delay(30_000); viewModel.onIntent(TaskListIntent.Poll) }
        }
    }
    TaskListScreen(state, viewModel::onIntent, onTaskClick, onRecoveryClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(state: TaskListUiState, onIntent: (TaskListIntent) -> Unit, onTaskClick: (Long) -> Unit, onRecoveryClick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Задания", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onRecoveryClick) { Text("Проверка действий") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TaskView.ACTIVE to "Доступные", TaskView.HISTORY to "История заданий").forEach { (view, title) ->
                FilterChip(selected = state.filter.view == view, onClick = { onIntent(TaskListIntent.FilterChanged(state.filter.copy(view = view))) }, label = { Text(title) })
            }
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { menu = true }) { Text(state.filter.type?.title() ?: "Все типы") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                (listOf<TaskType?>(null) + TaskType.entries.filter { it != TaskType.UNKNOWN }).forEach { type ->
                    DropdownMenuItem(text = { Text(type?.title() ?: "Все типы") }, onClick = { menu = false; onIntent(TaskListIntent.FilterChanged(state.filter.copy(type = type))) })
                }
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error); TextButton(onClick = { onIntent(TaskListIntent.Refresh) }) { Text("Повторить загрузку") } }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { onIntent(TaskListIntent.Refresh) }, modifier = Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize().testTag("task-list"), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                if (!state.loading && state.error == null && state.items.isEmpty()) item { Text("Доступных заданий пока нет. Потяните вниз для обновления") }
                items(state.items, key = Task::id) { task ->
                    val displayedLines = task.lines.filter { task.type != TaskType.MANUFACTURE_PRODUCT || it.role == "output" }
                    Card(Modifier.fillMaxWidth().testTag("task-card-${task.id}").clickable { onTaskClick(task.id) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(task.type.title(), style = MaterialTheme.typography.titleMedium)
                            if (task.isClosedIncomplete) {
                                Text("Закрыта без полного выполнения", color = MaterialTheme.colorScheme.primary)
                                task.closedIncompleteReason?.let { Text("Причина: $it") }
                            }
                            displayedLines.take(3).forEach { line ->
                                Text(line.productName)
                                if (state.filter.view != TaskView.ACTIVE) Text("Выполнено ${QuantityFormatter.format(line.actual)} ${line.baseUnit.orEmpty()}${line.actualSheets?.let { " · $it листов" }.orEmpty()}")
                                Text("Осталось ${QuantityFormatter.format(line.remaining)} ${line.baseUnit.orEmpty()}${line.remainingSheets?.let { " · $it листов" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                                line.sheetLength?.let { Text("Длина ${QuantityFormatter.format(it)} м") }
                            }
                            if (displayedLines.size > 3) Text("Ещё ${displayedLines.size - 3} позиций")
                        }
                    }
                }
                if (state.hasMore) item { OutlinedButton(onClick = { onIntent(TaskListIntent.LoadMore) }, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) { Text(if (state.loadingMore) "Загружаем…" else "Ещё задания") } }
            }
        }
    }
}
