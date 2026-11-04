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
fun RecoveryRoute(viewModel: RecoveryViewModel, onTaskClick: (Long) -> Unit, onCompleted: (CommandConfirmation) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompletedTaskEffect(state.lastConfirmation, onCompleted)
    RecoveryScreen(state, viewModel::onIntent, onTaskClick)
}
@Composable
fun RecoveryScreen(state: RecoveryUiState, onIntent: (RecoveryIntent) -> Unit, onTaskClick: (Long) -> Unit) {
    var showFinished by remember { mutableStateOf(false) }
    var lastRetry by remember { mutableStateOf<String?>(null) }
    val finished = state.commands.filter { it.state in setOf(PendingState.ACKNOWLEDGED, PendingState.REJECTED) }
    val visible = state.commands.filter { it !in finished || showFinished || it.id == lastRetry }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item { Text("Проверка действий", style = MaterialTheme.typography.headlineMedium); Text("Проверьте исход перед новым выполнением. Повтор отправит то же действие"); state.message?.let { Text(it) } }
        if (visible.isEmpty()) item { Text("Нет действий для проверки") }
        if (finished.isNotEmpty()) item { TextButton(onClick = { showFinished = !showFinished }) { Text(if (showFinished) "Скрыть завершённые" else "Завершённые") } }
        items(visible, key = PendingCommand::id) { command ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(command.summary.operationalTitle(), style = MaterialTheme.typography.titleMedium)
                Text(localDate(command.createdAt))
                command.summary.operationalLines().forEach { Text(it) }
                if (command.summary.comment.isNotBlank()) Text("Комментарий: ${command.summary.comment}")
                Text(when (command.state) {
                    PendingState.PREPARED -> "Сохранено перед отправкой"
                    PendingState.IN_FLIGHT -> "Отправляется"
                    PendingState.UNKNOWN -> "Исход неизвестен. Возможно, действие уже выполнено"
                    PendingState.NEEDS_REVIEW -> "Требуется сверка с сервером"
                    PendingState.ACKNOWLEDGED -> "Подтверждено сервером"
                    PendingState.REJECTED -> "Запрос отклонён сервером"
                })
                command.error?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error) }
                if (command.state == PendingState.NEEDS_REVIEW) Text("Не выполняйте заново. Повторите это действие; при повторном отказе обратитесь к руководителю")
                if (command.state !in setOf(PendingState.ACKNOWLEDGED, PendingState.REJECTED)) Button(onClick = { lastRetry = command.id; onIntent(RecoveryIntent.Retry(command.id)) }, enabled = command.id !in state.retrying) { Text(if (command.id in state.retrying) "Отправляем…" else "Повторить действие") }
                TextButton(onClick = { onTaskClick(command.taskId) }) { Text("К заданию") }
            } }
        }
    }
}
