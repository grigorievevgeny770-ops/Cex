package com.logunovinc.workshopmanager.feature.stock

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logunovinc.workshopmanager.core.ui.QuantityFormatter
import com.logunovinc.workshopmanager.domain.order.SheetRequestStatus

@Composable
fun SheetRequestRoute(viewModel: SheetRequestViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SheetRequestScreen(state, viewModel::onIntent)
}
@Composable
fun SheetRequestScreen(state: SheetRequestUiState, onIntent: (SheetRequestIntent) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Плоский лист", style = MaterialTheme.typography.headlineMedium)
            Text("Запрос уходит менеджеру. Принять лист можно после его подтверждения и отгрузки.")
            state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            state.pending.forEach { command ->
                Text(command.summary.title)
                command.summary.lines.forEach { Text(it) }
                Button(onClick = { onIntent(SheetRequestIntent.Retry(command.id)) }, enabled = !state.sending) { Text("Повторить сохранённое действие") }
            }
            TextButton(onClick = { onIntent(SheetRequestIntent.Refresh) }, enabled = !state.sending) { Text("Обновить") }
            OutlinedTextField(state.query, { onIntent(SheetRequestIntent.Query(it)) }, label = { Text("Найти плоский лист") }, modifier = Modifier.fillMaxWidth())
            state.choices.forEach { choice -> TextButton(onClick = { onIntent(SheetRequestIntent.Choose(choice.id)) }) { Text(if (state.chosen == choice.id) "Выбран: ${choice.name}" else choice.name) } }
            OutlinedTextField(state.length, { onIntent(SheetRequestIntent.Length(it)) }, label = { Text("Длина, м") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.count, { onIntent(SheetRequestIntent.Count(it)) }, label = { Text("Листов") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onIntent(SheetRequestIntent.Submit) }, enabled = !state.sending && state.pending.isEmpty() && state.chosen != null) { Text("Запросить лист") }
        }
        items(state.requests, key = { it.id }) { request ->
            Card { Column(Modifier.padding(16.dp)) {
                Text(request.productName, style = MaterialTheme.typography.titleMedium)
                Text("${request.sheets} лист. × ${QuantityFormatter.format(request.length)} м")
                Text(when (request.status) {
                    SheetRequestStatus.REQUESTED -> "Ждёт подтверждения менеджера"
                    SheetRequestStatus.CONFIRMED -> "Отгружен, можно принять"
                    SheetRequestStatus.RECEIVED -> "Принят"
                    SheetRequestStatus.CANCELLED -> "Отменён"
                    SheetRequestStatus.UNKNOWN -> "Статус неизвестен"
                })
                if (request.canReceive) Button(onClick = { onIntent(SheetRequestIntent.Receive(request.id)) }, enabled = !state.sending && state.pending.isEmpty()) { Text("Принять лист") }
            } }
        }
    }
    if (state.confirmation != null) AlertDialog(
        onDismissRequest = { onIntent(SheetRequestIntent.Dismiss) },
        title = { Text(if (state.confirmation == SheetRequestIntent.Submit) "Запросить лист?" else "Лист доставлен и принят?") },
        text = { Text(if (state.confirmation == SheetRequestIntent.Submit) "${state.choices.firstOrNull { it.id == state.chosen }?.name.orEmpty()} · ${state.count} лист. × ${state.length} м" else "Остаток цеха увеличится на всё количество в запросе.") },
        confirmButton = { Button(onClick = { onIntent(SheetRequestIntent.Confirm) }) { Text("Подтвердить") } },
        dismissButton = { TextButton(onClick = { onIntent(SheetRequestIntent.Dismiss) }) { Text("Назад") } },
    )
}
