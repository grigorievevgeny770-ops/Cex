package com.logunovinc.workshopmanager.feature.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logunovinc.workshopmanager.core.ui.QuantityFormatter
import com.logunovinc.workshopmanager.domain.task.*

@Composable
fun ReceiveExecutionRoute(viewModel: ReceiveExecutionViewModel, onRecoveryClick: () -> Unit, onDetailClick: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReceiveExecutionScreen(state, viewModel::onIntent, onRecoveryClick, onDetailClick)
}
@Composable
fun ReceiveExecutionScreen(state: ReceiveExecutionUiState, onIntent: (ReceiveExecutionIntent) -> Unit, onRecoveryClick: () -> Unit, onDetailClick: () -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val task = state.task
    LazyColumn(Modifier.fillMaxSize().testTag("receive-list").padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Приёмка · №${task?.id ?: ""}", style = MaterialTheme.typography.headlineMedium)
            Text("Введите только фактически прибывшее в этом шаге количество. Ранее принятые товары повторно не вводите")
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
            when (val result = state.result) {
                is ExecutionResultState.Confirmed -> { Text("Действие подтверждено сервером · версия ${result.receipt.taskVersion}", color = MaterialTheme.colorScheme.primary); TextButton(onClick = onDetailClick) { Text("Карточка и история") } }
                is ExecutionResultState.Unresolved -> { Text("Исход неизвестен: ${result.error.message}", color = MaterialTheme.colorScheme.error); TextButton(onClick = onRecoveryClick) { Text("Восстановить исходный запрос") } }
                is ExecutionResultState.Rejected -> Text(result.error.message, color = MaterialTheme.colorScheme.error)
                ExecutionResultState.Sending -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> Unit
            }
            state.pending?.let { pending -> Text("Есть сохранённый запрос: ${pending.summary.title}"); pending.summary.lines.forEach { Text(it) }; TextButton(onClick = onRecoveryClick) { Text("Открыть журнал запросов") } }
            if (task != null && !task.canExecute) Text("Выполнение сейчас недоступно. Обновите карточку или откройте историю")
            if (task?.status == TaskStatus.COMPLETED && task.outcome == "shortage") Text("Приёмка завершена с недостачей; менеджер разбирает расхождение")
        }
        items(task?.lines.orEmpty(), key = TaskLine::id) { line ->
            val input = state.inputs.firstOrNull { it.taskLineId == line.id } ?: ActualLineInput(line.id)
            val sheets = line.plannedSheets != null || line.remainingSheets != null
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(line.productName, style = MaterialTheme.typography.titleMedium)
                Text("Уже принято ${QuantityFormatter.format(line.actual)} · осталось ${QuantityFormatter.format(line.remaining)} ${line.baseUnit.orEmpty()}")
                if (sheets) {
                    Text("Листы: принято ${line.actualSheets ?: "—"} · осталось ${line.remainingSheets ?: "—"}")
                    line.sheetLength?.let { Text("Длина по заданию ${QuantityFormatter.format(it)} м") }
                    OutlinedTextField(value = input.sheets, onValueChange = { onIntent(ReceiveExecutionIntent.SheetsChanged(line.id, it)) }, label = { Text("Прибыло листов в этом шаге") }, enabled = state.editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                } else OutlinedTextField(value = input.quantity, onValueChange = { onIntent(ReceiveExecutionIntent.QuantityChanged(line.id, it)) }, label = { Text("Прибыло в этом шаге, ${line.baseUnit.orEmpty()}") }, enabled = state.editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("receive-qty-${line.id}"))
            } }
        }
        item {
            Row { Checkbox(checked = state.final, onCheckedChange = { onIntent(ReceiveExecutionIntent.FinalChanged(it)) }, enabled = state.editable); Text(if (state.final) "Завершить приёмку" else "Продолжить ожидание оставшегося") }
            if (state.final) OutlinedTextField(value = state.reason, onValueChange = { onIntent(ReceiveExecutionIntent.ReasonChanged(it)) }, label = { Text("Причина недостачи, если она есть") }, enabled = state.editable, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.comment, onValueChange = { onIntent(ReceiveExecutionIntent.CommentChanged(it)) }, label = { Text("Комментарий") }, enabled = state.editable, modifier = Modifier.fillMaxWidth())
            Button(onClick = { focus.clearFocus(); keyboard?.hide(); onIntent(ReceiveExecutionIntent.ReviewClick) }, modifier = Modifier.fillMaxWidth().testTag("receive-review"), enabled = state.editable) { Text("Проверить приёмку") }
            TextButton(onClick = { onIntent(ReceiveExecutionIntent.Refresh) }, enabled = state.result != ExecutionResultState.Sending) { Text("Обновить задание") }
        }
    }
    state.review?.let { review ->
        AlertDialog(onDismissRequest = { onIntent(ReceiveExecutionIntent.DismissReview) }, title = { Text(if (review.final) "Завершить приёмку?" else "Принять эту часть?") },
            text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (review.lines.isEmpty()) Text("В этом шаге не прибыло ни одного товара")
                review.lines.forEach { actual ->
                    val line = task?.lines?.firstOrNull { it.id == actual.taskLineId }
                    val amount = actual.sheets?.let { "$it листов" } ?: "${actual.quantity?.toPlainString()?.replace('.', ',')} ${line?.baseUnit.orEmpty()}"
                    Text("${line?.productName.orEmpty()}: $amount")
                }
                if (review.final && review.shortage) Text("Приёмка завершится с недостачей. Это не утверждение потери; решение примет менеджер. Причина: ${review.reason}")
                else if (!review.final) Text("Оставшийся товар продолжим ожидать. Недостача сейчас не утверждается")
                if (review.comment.isNotBlank()) Text(review.comment)
            } },
            confirmButton = { Button(onClick = { onIntent(ReceiveExecutionIntent.ConfirmClick) }, modifier = Modifier.testTag("receive-confirm"), enabled = state.result != ExecutionResultState.Sending && state.pending == null) { Text("Подтвердить") } },
            dismissButton = { TextButton(onClick = { onIntent(ReceiveExecutionIntent.DismissReview) }, enabled = state.result != ExecutionResultState.Sending) { Text("Изменить") } })
    }
}
