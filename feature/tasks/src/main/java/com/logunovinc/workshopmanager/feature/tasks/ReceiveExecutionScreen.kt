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
import com.logunovinc.workshopmanager.core.ui.RefreshableContent
import com.logunovinc.workshopmanager.domain.task.*

@Composable
fun ReceiveExecutionRoute(viewModel: ReceiveExecutionViewModel, onCompleted: (com.logunovinc.workshopmanager.domain.pending.CommandConfirmation) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompletedTaskEffect((state.result as? ExecutionResultState.Confirmed)?.receipt, onCompleted)
    ReceiveExecutionScreen(state, viewModel::onIntent)
}
@Composable
fun ReceiveExecutionScreen(state: ReceiveExecutionUiState, onIntent: (ReceiveExecutionIntent) -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val task = state.task
    RefreshableContent(state.refreshing, state.canRefresh, { onIntent(ReceiveExecutionIntent.Refresh) }) {
        LazyColumn(Modifier.fillMaxSize().testTag("receive-list").padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Приёмка", style = MaterialTheme.typography.headlineMedium)
                Text("Укажите, сколько прибыло сейчас")
                if (state.final) Text("Можно принять 0. Ранее принятое сохранится, недостачу разберёт руководитель")
                if (state.loading && !state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error) }
                when (val result = state.result) {
                    is ExecutionResultState.Confirmed -> { Text("Готово", color = MaterialTheme.colorScheme.primary) }
                    is ExecutionResultState.Unresolved -> { Text("Исход неизвестен: ${result.error.operationalMessage()}", color = MaterialTheme.colorScheme.error) }
                    is ExecutionResultState.Rejected -> Text(result.error.operationalMessage(), color = MaterialTheme.colorScheme.error)
                    ExecutionResultState.Sending -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> Unit
                }
                state.pending?.let { pending -> Text("Не подтверждено: ${pending.summary.operationalTitle()}"); pending.summary.operationalLines().forEach { Text(it) } }
                if (task != null && !task.canExecute) Text("Приёмка недоступна")
                if (task?.isCompleted == true && task.outcome == "shortage") Text("Приёмка завершена. Недостачу разберёт руководитель")
            }
            items(task?.lines.orEmpty(), key = TaskLine::id) { line ->
                val input = state.inputs.firstOrNull { it.taskLineId == line.id } ?: ActualLineInput(line.id)
                val sheets = line.plannedSheets != null || line.remainingSheets != null
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(line.productName, style = MaterialTheme.typography.titleMedium)
                    Text("План ${QuantityFormatter.format(line.planned)} · уже принято ${QuantityFormatter.format(line.actual)} · осталось ${QuantityFormatter.format(line.remaining)} ${line.baseUnit.orEmpty()}")
                    if (sheets) {
                        Text("Листы: принято ${line.actualSheets ?: "—"} · осталось ${line.remainingSheets ?: "—"}")
                        line.sheetLength?.let { Text("Длина ${QuantityFormatter.format(it)} м") }
                        OutlinedTextField(value = input.sheets, onValueChange = { onIntent(ReceiveExecutionIntent.SheetsChanged(line.id, it)) }, label = { Text("Прибыло листов в этом шаге") }, enabled = state.editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    } else OutlinedTextField(value = input.quantity, onValueChange = { onIntent(ReceiveExecutionIntent.QuantityChanged(line.id, it)) }, label = { Text("Принять сейчас, ${line.baseUnit.orEmpty()}") }, enabled = state.editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("receive-qty-${line.id}"))
                } }
            }
            item {
                Row { Checkbox(checked = state.final, onCheckedChange = { onIntent(ReceiveExecutionIntent.FinalChanged(it)) }, enabled = state.editable); Text(if (state.final) "Завершить приёмку" else "Ждать остальное") }
                if (state.final) OutlinedTextField(value = state.reason, onValueChange = { onIntent(ReceiveExecutionIntent.ReasonChanged(it)) }, label = { Text("Причина недостачи, если она есть") }, enabled = state.editable, modifier = Modifier.fillMaxWidth())
                Button(onClick = { focus.clearFocus(); keyboard?.hide(); onIntent(ReceiveExecutionIntent.ReviewClick) }, modifier = Modifier.fillMaxWidth().testTag("receive-review"), enabled = state.editable) { Text("Принять") }
            }
        }
    }
    state.review?.let { review ->
        AlertDialog(onDismissRequest = { onIntent(ReceiveExecutionIntent.DismissReview) }, title = { Text(if (review.final) "Завершить приёмку?" else "Принять эту часть?") },
            text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (review.final) task?.lines?.forEach { line ->
                    Text(line.productName)
                    Text("План ${QuantityFormatter.format(line.planned)} · ранее принято ${QuantityFormatter.format(line.actual)} · остаток до этого шага ${QuantityFormatter.format(line.remaining)} ${line.baseUnit.orEmpty()}")
                    line.remainingSheets?.let { remaining -> Text("Листы: план ${line.plannedSheets ?: "—"} · ранее принято ${line.actualSheets ?: "—"} · остаток до этого шага $remaining") }
                }
                if (review.lines.isEmpty()) Text("В этом шаге не прибыло ни одного товара")
                review.lines.forEach { actual ->
                    val line = task?.lines?.firstOrNull { it.id == actual.taskLineId }
                    val amount = actual.sheets?.let { "$it листов" } ?: "${actual.quantity?.toPlainString()?.replace('.', ',')} ${line?.baseUnit.orEmpty()}"
                    Text("${line?.productName.orEmpty()}: $amount")
                    line?.sheetLength?.let { Text("Длина ${QuantityFormatter.format(it)} м") }
                }
                if (review.final && review.shortage) Text("Остальное станет недостачей. Потерю это не утверждает. Причина: ${review.reason}")
                else if (!review.final) Text("Остальное продолжим ждать")
            } },
            confirmButton = { Button(onClick = { onIntent(ReceiveExecutionIntent.ConfirmClick) }, modifier = Modifier.testTag("receive-confirm"), enabled = state.result != ExecutionResultState.Sending && state.pending == null) { Text("Подтвердить") } },
            dismissButton = { TextButton(onClick = { onIntent(ReceiveExecutionIntent.DismissReview) }, enabled = state.result != ExecutionResultState.Sending) { Text("Изменить") } })
    }
}
