package com.logunovinc.workshopmanager.feature.stock

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
import kotlinx.coroutines.awaitCancellation
import com.logunovinc.workshopmanager.core.ui.QuantityFormatter
import com.logunovinc.workshopmanager.core.ui.RefreshableContent
import com.logunovinc.workshopmanager.domain.order.StockSummaryLine
import com.logunovinc.workshopmanager.domain.stock.*

@Composable
fun StockListRoute(viewModel: StockListViewModel, onPositionClick: (Long) -> Unit, onSheets: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        if (!viewModel.uiState.value.loading) viewModel.onIntent(StockListIntent.Refresh)
        awaitCancellation()
    } }
    StockListScreen(state, viewModel::onIntent, onPositionClick, onSheets)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockListScreen(state: StockListUiState, onIntent: (StockListIntent) -> Unit, onPositionClick: (Long) -> Unit, onSheets: () -> Unit = {}) {
    RefreshableContent(
        refreshing = state.pullRefreshing,
        enabled = !state.loading && !state.refreshing && !state.loadingMore,
        onRefresh = { onIntent(StockListIntent.PullRefresh) },
        automaticLoading = state.loading || state.refreshing || state.loadingMore,
    ) {
    Column(Modifier.fillMaxSize().padding(start = 16.dp, top = 12.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (state.summary == null) "Склад цеха" else "Остатки", style = MaterialTheme.typography.headlineMedium)
        if (state.summary == null) OutlinedTextField(value = state.filter.query, onValueChange = { onIntent(StockListIntent.QueryChanged(it)) }, label = { Text("Найти товар") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (state.showSheetRequests) OutlinedButton(onClick = onSheets, modifier = Modifier.testTag("sheet-requests")) { Text("Плоский лист") }
        state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error); TextButton(onClick = { onIntent(StockListIntent.Refresh) }) { Text("Повторить") } }
            LazyColumn(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                val summary = state.summary
                if (summary != null) {
                    item { Text("Плоский лист", style = MaterialTheme.typography.titleLarge) }
                    if (summary.flatSheet.isEmpty()) item { Text("Плоского листа нет") }
                    items(summary.flatSheet, key = { "sheet-${it.productId}-${it.size}" }) { SummaryLine(it) }
                    item { Text("Готовая продукция, ожидающая передачи", style = MaterialTheme.typography.titleLarge) }
                    if (summary.awaitingTransfer.isEmpty()) item { Text("Ничего не ожидает передачи") }
                    items(summary.awaitingTransfer, key = { "wait-${it.productId}-${it.size}" }) { SummaryLine(it) }
                    item { Text("Передано за день", style = MaterialTheme.typography.titleLarge) }
                    if (summary.transferredOn.isEmpty()) item { Text("Сегодня ещё ничего не передано") }
                    items(summary.transferredOn, key = { "sent-${it.productId}-${it.size}" }) { SummaryLine(it) }
                }
                if (summary == null && state.items.isEmpty() && !state.loading && state.error == null) item { Text("Позиций по этому запросу нет") }
                if (summary == null) items(state.items, key = StockPosition::productId) { position ->
                    Card(Modifier.fillMaxWidth().clickable { onPositionClick(position.productId) }) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(position.productName ?: "Товар", style = MaterialTheme.typography.titleMedium)
                        StockTotals(position)
                        position.dimensions.take(3).forEach { dimension ->
                            Text("${QuantityFormatter.format(dimension.length)} м · ${dimension.sheets} листов · свободно ${dimension.freeSheets?.toString() ?: "неизвестно"}")
                        }
                        if (position.dimensions.any { it.free == null || it.freeSheets == null }) Text("Свободный остаток по размерам неизвестен")
                    } }
                }
                if (state.hasMore) item { OutlinedButton(onClick = { onIntent(StockListIntent.LoadMore) }, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) { Text(if (state.loadingMore) "Загружаем…" else "Ещё товары") } }
            }
        }
    }
}
@Composable
fun StockDetailRoute(viewModel: StockDetailViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        if (!viewModel.uiState.value.loading) viewModel.onIntent(StockDetailIntent.Refresh)
        awaitCancellation()
    } }
    StockDetailScreen(state, viewModel::onIntent)
}
@Composable
fun StockDetailScreen(state: StockDetailUiState, onIntent: (StockDetailIntent) -> Unit) {
    val position = state.position
    RefreshableContent(state.refreshing, !state.loading, { onIntent(StockDetailIntent.PullRefresh) }, automaticLoading = state.loading) {
        LazyColumn(Modifier.fillMaxSize().testTag("stock-detail-list").padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                Text(position?.productName ?: "Позиция склада", style = MaterialTheme.typography.headlineMedium)
                state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            }
            if (position != null) {
                item {
                    StockTotals(position)
                    if (position.unallocatedStock.signum() != 0 && position.dimensions.isNotEmpty()) Text("Без размера: ${QuantityFormatter.format(position.unallocatedStock)} ${position.baseUnit}")
                    if (position.dimensions.isNotEmpty()) Text("Размеры", style = MaterialTheme.typography.titleLarge)
                }
                items(position.dimensions) { dimension ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Длина ${QuantityFormatter.format(dimension.length)} м", style = MaterialTheme.typography.titleMedium)
                        Text("Физически ${QuantityFormatter.format(dimension.quantity)} ${position.baseUnit} · ${dimension.sheets} листов")
                        Text("Свободно ${dimension.free?.let(QuantityFormatter::format) ?: "неизвестно"} ${position.baseUnit}")
                        Text("Свободных листов: ${dimension.freeSheets?.toString() ?: "неизвестно"}")
                    } }
                }
            }
        }
    }
}
@Composable
private fun SummaryLine(line: StockSummaryLine) {
    Text("${line.productName}${line.size?.let { " · $it" }.orEmpty()}: ${QuantityFormatter.format(line.quantity)} ${line.unit}${line.sheets?.let { " · $it листов" }.orEmpty()}")
}
@Composable
private fun StockTotals(position: StockPosition) {
    Text("На складе: ${QuantityFormatter.format(position.quantity)} ${position.baseUnit}")
    if (position.free.compareTo(position.quantity) != 0) Text("Доступно: ${QuantityFormatter.format(position.free)} ${position.baseUnit}", color = if (position.free.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
}
