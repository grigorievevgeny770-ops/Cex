package com.logunovinc.workshopmanager.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
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
fun TaskListRoute(viewModel: TaskListViewModel, onTaskClick: (Long) -> Unit, consumeCompletion: () -> String? = { null }) {
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
    TaskListScreen(state, viewModel::onIntent, onTaskClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(state: TaskListUiState, onIntent: (TaskListIntent) -> Unit, onTaskClick: (Long) -> Unit) {
    RefreshableContent(
        refreshing = state.pullRefreshing,
        enabled = !state.loading && !state.refreshing && !state.loadingMore,
        onRefresh = { onIntent(TaskListIntent.PullRefresh) },
        automaticLoading = state.loading || state.refreshing || state.loadingMore,
    ) {
    Column(Modifier.fillMaxSize().padding(start = 16.dp, top = 12.dp, end = 16.dp)) {
        Text("Заявки", style = MaterialTheme.typography.headlineMedium)
        Text("Изделия, которые сервер поставил в изготовление")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TaskView.ACTIVE to "Актуальные", TaskView.HISTORY to "История").forEach { (view, title) ->
                FilterChip(selected = state.filter.view == view, onClick = { onIntent(TaskListIntent.FilterChanged(state.filter.copy(view = view))) }, label = { Text(title) })
            }
        }
        state.error?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error); TextButton(onClick = { onIntent(TaskListIntent.Refresh) }) { Text("Повторить загрузку") } }
            LazyColumn(Modifier.weight(1f).fillMaxSize().testTag("task-list"), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                if (!state.loading && state.error == null && state.items.isEmpty()) item { Text(if (state.filter.view == TaskView.HISTORY) "В истории заявок пока ничего нет" else "Актуальных заявок нет. Потяните вниз для обновления") }
                items(state.items, key = Task::id) { task ->
                    Card(Modifier.fillMaxWidth().testTag("task-card-${task.id}").clickable { onTaskClick(task.id) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(task.workshopStage().title(), style = MaterialTheme.typography.titleMedium)
                            val products = task.productRequestLines()
                            if (products.isEmpty()) Text("В этой карточке нет изделий заявки")
                            products.forEach { line -> RequestLineText(line) }
                            Text(task.dueAt?.let { "Нужно к ${localDay(it)}" } ?: "Дата не указана")
                            if (task.comment.isNotBlank()) Text(task.comment)
                            if (task.isClosedIncomplete) task.closedIncompleteReason?.let { Text("Причина: $it") }
                        }
                    }
                }
                if (state.hasMore) item { OutlinedButton(onClick = { onIntent(TaskListIntent.LoadMore) }, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) { Text(if (state.loadingMore) "Загружаем…" else "Ещё задания") } }
            }
        }
    }
}

@Composable
internal fun RequestLineText(line: TaskLine) {
    Text(line.productName, style = MaterialTheme.typography.titleMedium)
    Text("Количество ${QuantityFormatter.format(line.planned)} ${line.baseUnit.orEmpty()}")
    line.sheetLength?.let { Text("Размер ${QuantityFormatter.format(it)} м") }
}
