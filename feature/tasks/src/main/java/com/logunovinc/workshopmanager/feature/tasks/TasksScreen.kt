package com.logunovinc.workshopmanager.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
fun TaskListRoute(viewModel: TaskListViewModel, onTaskClick: (Long) -> Unit, onRecoveryClick: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onIntent(TaskListIntent.Poll)
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
            TextButton(onClick = onRecoveryClick) { Text("Журнал запросов") }
        }
        state.warehouse?.let { Text(it.name, style = MaterialTheme.typography.bodySmall) }
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
        state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error); TextButton(onClick = { onIntent(TaskListIntent.Refresh) }) { Text("Повторить загрузку") } }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { onIntent(TaskListIntent.Refresh) }, modifier = Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                if (!state.loading && state.error == null && state.items.isEmpty()) item { Text("Доступных заданий пока нет. Потяните вниз для обновления") }
                items(state.items, key = Task::id) { task ->
                    Card(Modifier.fillMaxWidth().clickable { onTaskClick(task.id) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("№${task.id} · ${task.type.title()}", style = MaterialTheme.typography.titleMedium)
                            task.lines.take(3).forEach { line ->
                                Text(line.productName)
                                Text("План ${QuantityFormatter.format(line.planned)} · факт ${QuantityFormatter.format(line.actual)} · осталось ${QuantityFormatter.format(line.remaining)} ${line.baseUnit.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (task.lines.size > 3) Text("Ещё ${task.lines.size - 3} позиций")
                            Text("Срок: ${localDate(task.dueAt)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (state.hasMore) item { OutlinedButton(onClick = { onIntent(TaskListIntent.LoadMore) }, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) { Text(if (state.loadingMore) "Загружаем…" else "Ещё задания (${state.items.size} из ${state.total})") } }
            }
        }
    }
}
