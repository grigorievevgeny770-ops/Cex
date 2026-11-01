package com.logunovinc.workshopmanager.feature.stock

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
import kotlinx.coroutines.awaitCancellation
import com.logunovinc.workshopmanager.core.ui.QuantityFormatter
import com.logunovinc.workshopmanager.domain.stock.*

@Composable
fun StockListRoute(viewModel: StockListViewModel, onPositionClick: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        if (!viewModel.uiState.value.loading) viewModel.onIntent(StockListIntent.Refresh)
        awaitCancellation()
    } }
    StockListScreen(state, viewModel::onIntent, onPositionClick)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockListScreen(state: StockListUiState, onIntent: (StockListIntent) -> Unit, onPositionClick: (Long) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Склад цеха", style = MaterialTheme.typography.headlineMedium)
        state.warehouse?.let { Text(it.name) }
        OutlinedTextField(value = state.filter.query, onValueChange = { onIntent(StockListIntent.QueryChanged(it)) }, label = { Text("Поиск по всему складу") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row { Switch(checked = state.filter.onlyAvailable, onCheckedChange = { onIntent(StockListIntent.OnlyAvailableChanged(it)) }); Text("Только свободные") }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error); TextButton(onClick = { onIntent(StockListIntent.Refresh) }) { Text("Повторить") } }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { onIntent(StockListIntent.Refresh) }, modifier = Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                if (state.items.isEmpty() && !state.loading && state.error == null) item { Text("Позиций по этому запросу нет") }
                items(state.items, key = StockPosition::productId) { position ->
                    Card(Modifier.fillMaxWidth().clickable { onPositionClick(position.productId) }) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(position.productName ?: "Товар №${position.productId}", style = MaterialTheme.typography.titleMedium)
                        position.sku?.let { Text("Артикул $it") }
                        StockTotals(position)
                        if (position.dimensions.isNotEmpty()) Text("Размеров: ${position.dimensions.size}")
                        if (position.dimensions.any { it.free == null || it.freeSheets == null }) Text("Свободный остаток по размерам неизвестен")
                    } }
                }
                if (state.hasMore) item { OutlinedButton(onClick = { onIntent(StockListIntent.LoadMore) }, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) { Text(if (state.loadingMore) "Загружаем…" else "Ещё позиции (${state.items.size} из ${state.total})") } }
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
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Text(position?.productName ?: "Позиция склада", style = MaterialTheme.typography.headlineMedium)
            state.warehouse?.let { Text(it.name) }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { onIntent(StockDetailIntent.Refresh) }) { Text("Обновить") }
        }
        if (position != null) {
            item {
                position.sku?.let { Text("Артикул $it") }
                Text("Товар №${position.productId}")
                StockTotals(position)
                Text("Без распределения по размерам: физически ${QuantityFormatter.format(position.unallocatedStock)}, резерв ${QuantityFormatter.format(position.unallocatedReserved)} ${position.baseUnit}")
                Text("Размеры и листы", style = MaterialTheme.typography.titleLarge)
                if (position.dimensions.isEmpty()) Text("Размерных остатков нет")
            }
            items(position.dimensions) { dimension ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Длина ${QuantityFormatter.format(dimension.length)} м", style = MaterialTheme.typography.titleMedium)
                    Text("Физически ${QuantityFormatter.format(dimension.quantity)} ${position.baseUnit} · ${dimension.sheets} листов")
                    Text("Резерв ${QuantityFormatter.format(dimension.reserved)} ${position.baseUnit} · ${dimension.reservedSheets} листов")
                    Text("Свободно ${dimension.free?.let(QuantityFormatter::format) ?: "неизвестно"} ${position.baseUnit}")
                    Text("Свободных листов: ${dimension.freeSheets?.toString() ?: "неизвестно"}")
                } }
            }
        }
    }
}
@Composable
private fun StockTotals(position: StockPosition) {
    Text("Физически: ${QuantityFormatter.format(position.quantity)} ${position.baseUnit}")
    Text("В резерве: ${QuantityFormatter.format(position.reserved)} ${position.baseUnit}")
    Text("Свободно: ${QuantityFormatter.format(position.free)} ${position.baseUnit}", color = if (position.free.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
}
