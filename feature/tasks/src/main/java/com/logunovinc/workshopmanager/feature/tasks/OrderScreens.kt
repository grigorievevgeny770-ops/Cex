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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logunovinc.workshopmanager.core.ui.QuantityFormatter
import com.logunovinc.workshopmanager.core.ui.localDay
import com.logunovinc.workshopmanager.domain.order.*
import com.logunovinc.workshopmanager.domain.pending.CommandConfirmation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderListScreen(state: TaskListUiState, onIntent: (TaskListIntent) -> Unit, onOrderClick: (Long) -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 16.dp, top = 12.dp, end = 16.dp)) {
        Text("Заявки", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.filter.view != com.logunovinc.workshopmanager.domain.task.TaskView.HISTORY, onClick = { onIntent(TaskListIntent.OrderViewChanged(false)) }, label = { Text("Актуальные") })
            FilterChip(selected = state.filter.view == com.logunovinc.workshopmanager.domain.task.TaskView.HISTORY, onClick = { onIntent(TaskListIntent.OrderViewChanged(true)) }, label = { Text("История") })
        }
        state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onIntent(TaskListIntent.Refresh) }, enabled = !state.loading && !state.refreshing) { Text("Обновить") }
        LazyColumn(Modifier.weight(1f).testTag("task-list"), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            if (!state.loading && state.error == null && state.orders.isNullOrEmpty()) item { Text("Актуальных заявок нет") }
            items(state.orders.orEmpty(), key = WorkshopOrder::id) { order ->
                Card(Modifier.fillMaxWidth().testTag("order-card-${order.id}").clickable { onOrderClick(order.id) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(order.status.title(), style = MaterialTheme.typography.titleMedium)
                        order.lines.forEach { line ->
                            Text(line.productName, style = MaterialTheme.typography.titleMedium)
                            Text("Количество ${QuantityFormatter.format(line.quantity)} ${line.unit}")
                            line.size?.let { Text("Размер $it") }
                        }
                        Text(order.dueAt?.let { "Нужно к ${localDay(it)}" } ?: "Дата не указана")
                        if (order.comment.isNotBlank()) Text(order.comment)
                    }
                }
            }
            if (state.hasMore) item { Button(onClick = { onIntent(TaskListIntent.LoadMore) }, enabled = !state.loadingMore) { Text("Показать ещё") } }
        }
    }
}

@Composable
fun OrderDetailRoute(viewModel: OrderDetailViewModel, onCompleted: (CommandConfirmation) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompletedTaskEffect(state.lastConfirmation, onCompleted)
    OrderDetailScreen(state, viewModel::onIntent)
}

@Composable
fun OrderDetailScreen(state: OrderDetailUiState, onIntent: (OrderDetailIntent) -> Unit) {
    val order = state.order
    LazyColumn(Modifier.fillMaxSize().padding(16.dp).testTag("order-detail"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Заявка", style = MaterialTheme.typography.headlineSmall)
            if (state.loading || state.sending || state.retrying) LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = { onIntent(OrderDetailIntent.Refresh) }, enabled = !state.loading && !state.sending && !state.retrying && state.confirm == null) { Text("Обновить") }
            state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            state.pending?.let {
                Text(it.summary.operationalTitle())
                Button(onClick = { onIntent(OrderDetailIntent.Retry) }, enabled = !state.retrying, modifier = Modifier.testTag("order-retry")) { Text("Повторить действие") }
            }
        }
        if (order != null) {
            item {
                Text(order.status.title(), style = MaterialTheme.typography.titleMedium)
                Text(order.dueAt?.let { "Нужно к ${localDay(it)}" } ?: "Дата не указана")
                if (order.comment.isNotBlank()) Text(order.comment)
                if (order.normUnspecified && order.canManufacture) Text("Сервер не сообщил, есть ли норма. Изготовление пока нельзя отметить.")
            }
            items(order.lines, key = { "line-${it.id}" }) { line ->
                Card { Column(Modifier.padding(16.dp)) {
                    Text(line.productName, style = MaterialTheme.typography.titleMedium)
                    if(line.color.isNotBlank()||line.thicknessMm.isNotBlank())Text("Цвет ${line.color} · толщина ${line.thicknessMm} мм")
                    Text("Количество ${QuantityFormatter.format(line.quantity)} ${line.unit}")
                    line.size?.let { Text("Размер $it") }
                    if(line.drawingVersion==null)Text(when (line.normKnown) { true -> "Норма есть на сервере"; false -> "Нормы нет: нужен фактический расход листа"; null -> "Про норму сервер ничего не сообщил" })
                    state.drawings[line.id]?.let { ProfileDrawingContent(it) }
                    state.drawingErrors[line.id]?.let { Text(it.message,color=MaterialTheme.colorScheme.error) }
                } }
            }
            item {
                if (order.canStart) Button(onClick = { onIntent(OrderDetailIntent.Start) }, modifier = Modifier.fillMaxWidth().testTag("order-start")) { Text("Взять в работу") }
                if (order.canManufacture && !order.normUnspecified) Button(onClick = { onIntent(OrderDetailIntent.Manufacture) }, modifier = Modifier.fillMaxWidth().testTag("order-manufacture")) { Text("Изготовлено") }
                if(order.canManufactureArea)Button(onClick={onIntent(OrderDetailIntent.ManufactureArea)},enabled=!state.loading&&!state.sending&&state.pending==null&&order.lines.all { line -> line.drawingVersion!=null&&state.drawings[line.id]?.version==line.drawingVersion },modifier=Modifier.fillMaxWidth().testTag("order-manufacture-area")){Text("Изготовлено · расход по чертежам")}
                if (order.canHandOver) Button(onClick = { onIntent(OrderDetailIntent.HandOver) }, modifier = Modifier.fillMaxWidth().testTag("order-hand-over")) { Text("Передано на склад") }
                if (order.canReverseManufacture) OutlinedButton(onClick = { onIntent(OrderDetailIntent.ReverseManufacture) }, modifier = Modifier.fillMaxWidth().testTag("order-reverse-manufacture")) { Text("Отменить изготовление") }
                if (order.canReverseHandOver) OutlinedButton(onClick = { onIntent(OrderDetailIntent.ReverseHandOver) }, modifier = Modifier.fillMaxWidth().testTag("order-reverse-hand-over")) { Text("Отменить передачу") }
            }
        }
    }
    val confirm = state.confirm
    if (confirm != null && order != null) {
        AlertDialog(onDismissRequest = { if (!state.sending) onIntent(OrderDetailIntent.Dismiss) },
            title = { Text(when (confirm) {
                OrderConfirm.Start -> "Взять заявку в работу?"
                OrderConfirm.Manufacture -> "Отметить, что всё изготовлено?"
                OrderConfirm.ManufactureArea -> "Все изделия изготовлены?"
                OrderConfirm.HandOver -> "Передать всё на склад?"
                is OrderConfirm.Reverse -> "Отменить шаг? Движения будут обращены, история сохранится."
            }) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (confirm == OrderConfirm.Manufacture && order.needsActualSheet) {
                    Text("Нормы нет. Укажите фактический расход только плоского листа.")
                    OutlinedTextField(state.sheetQuery, { onIntent(OrderDetailIntent.SheetQuery(it)) }, label = { Text("Найти плоский лист") }, modifier = Modifier.fillMaxWidth())
                    state.sheetChoices.forEach { choice -> TextButton(onClick = { onIntent(OrderDetailIntent.ChooseSheet(choice.id)) }) { Text(choice.name) } }
                    OutlinedTextField(state.sheetLength, { onIntent(OrderDetailIntent.SheetLength(it)) }, label = { Text("Длина, м") }, modifier = Modifier.fillMaxWidth().testTag("order-sheet-length"))
                    OutlinedTextField(state.sheetCount, { onIntent(OrderDetailIntent.SheetCount(it)) }, label = { Text("Листов") }, modifier = Modifier.fillMaxWidth().testTag("order-sheet-count"))
                    TextButton(onClick = { onIntent(OrderDetailIntent.AddSheet) }) { Text("Добавить расход") }
                    state.materials.forEachIndexed { index, material -> Text("${material.sheets} лист. × ${QuantityFormatter.format(material.length)} м") ; TextButton(onClick = { onIntent(OrderDetailIntent.RemoveSheet(index)) }) { Text("Убрать") } }
                } else if (confirm == OrderConfirm.Manufacture) Text("Сервер спишет лист по своей норме. Количество изделий не меняется.")
                if(confirm==OrderConfirm.ManufactureArea)Text("Сервер рассчитает и спишет площадь плоского листа по сохранённым чертежам. Выпускается вся заявка.")
                order.lines.forEach { Text("${it.productName}: ${QuantityFormatter.format(it.quantity)} ${it.unit}") }
            } },
            confirmButton = { Button(onClick = { onIntent(OrderDetailIntent.Confirm) }, enabled = !state.sending, modifier = Modifier.testTag("order-confirm")) { Text(if (state.sending) "Отправляем…" else "Подтвердить") } },
            dismissButton = { TextButton(onClick = { onIntent(OrderDetailIntent.Dismiss) }, enabled = !state.sending) { Text("Назад") } })
    }
}

private fun OrderStatus.title() = when (this) {
    OrderStatus.NEW -> "Новая"
    OrderStatus.IN_PROGRESS -> "В работе"
    OrderStatus.MANUFACTURED -> "Изготовлено"
    OrderStatus.HANDED_TO_WAREHOUSE -> "Передано на склад"
    OrderStatus.CANCELLED -> "Отменена"
    OrderStatus.UNKNOWN -> "Статус неизвестен"
}
