package com.logunovinc.workshopmanager.feature.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logunovinc.workshopmanager.core.ui.localDate
import com.logunovinc.workshopmanager.domain.pending.*

@Composable
fun RecoveryRoute(viewModel: RecoveryViewModel, onTaskClick: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecoveryScreen(state, viewModel::onIntent, onTaskClick)
}
@Composable
fun RecoveryScreen(state: RecoveryUiState, onIntent: (RecoveryIntent) -> Unit, onTaskClick: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item { Text("Журнал запросов", style = MaterialTheme.typography.headlineMedium); Text("Сохранённые подтверждённые вами действия этого аккаунта и сервера. Повтор сохраняет исходный состав запроса"); state.message?.let { Text(it) } }
        if (state.commands.isEmpty()) item { Text("Сохранённых команд нет") }
        items(state.commands, key = PendingCommand::id) { command ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(command.summary.title, style = MaterialTheme.typography.titleMedium)
                Text(localDate(command.createdAt))
                command.summary.lines.forEach { Text(it) }
                if (command.summary.comment.isNotBlank()) Text("Комментарий: ${command.summary.comment}")
                Text(when (command.state) {
                    PendingState.PREPARED -> "Сохранено перед отправкой"
                    PendingState.IN_FLIGHT -> "Отправляется"
                    PendingState.UNKNOWN -> "Исход неизвестен; это сохранённый запрос, а не черновик"
                    PendingState.NEEDS_REVIEW -> "Требуется сверка с сервером"
                    PendingState.ACKNOWLEDGED -> "Подтверждено сервером"
                    PendingState.REJECTED -> "Запрос отклонён сервером"
                })
                command.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
                command.knownExecutionId?.let { Text("Известный факт №$it — проверьте его в карточке задания") }
                if (command.state == PendingState.NEEDS_REVIEW) Text("Не создавайте новую команду вместо этой. Восстановите доступ тем же аккаунтом и повторите исходный запрос; если отказ остаётся, нужна сверка администратором сервера")
                if (command.state !in setOf(PendingState.ACKNOWLEDGED, PendingState.REJECTED)) Button(onClick = { onIntent(RecoveryIntent.Retry(command.id)) }, enabled = command.id !in state.retrying) { Text(if (command.id in state.retrying) "Отправляем исходный запрос…" else "Повторить исходный запрос") }
                TextButton(onClick = { onTaskClick(command.taskId) }) { Text("Открыть задание №${command.taskId}") }
            } }
        }
    }
}
