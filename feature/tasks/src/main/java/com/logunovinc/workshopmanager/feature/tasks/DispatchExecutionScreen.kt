package com.logunovinc.workshopmanager.feature.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
fun DispatchExecutionRoute(viewModel: DispatchExecutionViewModel, onDetailClick: () -> Unit, onCompleted: (com.logunovinc.workshopmanager.domain.pending.CommandConfirmation) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompletedTaskEffect((state.result as? ExecutionResultState.Confirmed)?.receipt, onCompleted)
    DispatchExecutionScreen(state, viewModel::onIntent, onDetailClick)
}
@Composable
fun DispatchExecutionScreen(state: DispatchExecutionUiState, onIntent: (DispatchExecutionIntent) -> Unit, onDetailClick: () -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val task = state.task
    LazyColumn(Modifier.fillMaxSize().testTag("dispatch-list").padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Отправка", style = MaterialTheme.typography.headlineMedium)
            Text("Укажите, сколько отправляете сейчас")
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it.operationalMessage(), color = MaterialTheme.colorScheme.error) }
            when (val result = state.result) {
                is ExecutionResultState.Confirmed -> { Text("Готово", color = MaterialTheme.colorScheme.primary); Text("Товар в пути"); TextButton(onClick = onDetailClick) { Text("К заданию") } }
                is ExecutionResultState.Unresolved -> { Text("Исход неизвестен: ${result.error.operationalMessage()}", color = MaterialTheme.colorScheme.error); if (state.pending == null) TextButton(onClick = onDetailClick) { Text("К заданию") } }
                is ExecutionResultState.Rejected -> Text(result.error.operationalMessage(), color = MaterialTheme.colorScheme.error)
                ExecutionResultState.Sending -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> Unit
            }
            state.pending?.let { pending -> Text("Не подтверждено: ${pending.summary.operationalTitle()}"); pending.summary.operationalLines().forEach { Text(it) }; TextButton(onClick = onDetailClick) { Text("К заданию") } }
            if (task != null && !task.canExecute) Text("Отправка недоступна")
        }
        items(task?.lines.orEmpty(), key = TaskLine::id) { line ->
            val input = state.inputs.firstOrNull { it.taskLineId == line.id } ?: ActualLineInput(line.id)
            val sheets = line.plannedSheets != null || line.remainingSheets != null
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(line.productName, style = MaterialTheme.typography.titleMedium)
                Text("Уже отправлено ${QuantityFormatter.format(line.actual)} · осталось ${QuantityFormatter.format(line.remaining)} ${line.baseUnit.orEmpty()}")
                if (sheets) {
                    Text("Листы: отправлено ${line.actualSheets ?: "—"} · осталось ${line.remainingSheets ?: "—"}")
                    line.sheetLength?.let { Text("Длина ${QuantityFormatter.format(it)} м") }
                    OutlinedTextField(value = input.sheets, onValueChange = { onIntent(DispatchExecutionIntent.SheetsChanged(line.id, it)) }, label = { Text("Отдано листов в этом шаге") }, enabled = state.editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                } else OutlinedTextField(value = input.quantity, onValueChange = { onIntent(DispatchExecutionIntent.QuantityChanged(line.id, it)) }, label = { Text("Отправить сейчас, ${line.baseUnit.orEmpty()}") }, enabled = state.editable, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            } }
        }
        item {
            Button(onClick = { focus.clearFocus(); keyboard?.hide(); onIntent(DispatchExecutionIntent.ReviewClick) }, enabled = state.editable, modifier = Modifier.fillMaxWidth()) { Text("Отправить") }
            TextButton(onClick = { onIntent(DispatchExecutionIntent.Refresh) }, enabled = state.result != ExecutionResultState.Sending) { Text("Обновить") }
        }
    }
    state.review?.let { review ->
        AlertDialog(onDismissRequest = { onIntent(DispatchExecutionIntent.DismissReview) }, title = { Text("Подтвердить отправку?") },
            text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                review.lines.forEach { actual ->
                    val line = task?.lines?.firstOrNull { it.id == actual.taskLineId }
                    val amount = actual.sheets?.let { "$it листов" } ?: "${actual.quantity?.toPlainString()?.replace('.', ',')} ${line?.baseUnit.orEmpty()}"
                    Text("${line?.productName.orEmpty()}: $amount")
                    line?.sheetLength?.let { Text("Длина ${QuantityFormatter.format(it)} м") }
                }
                Text("Товар будет в пути до приёмки получателем")
            } },
            confirmButton = { Button(onClick = { onIntent(DispatchExecutionIntent.ConfirmClick) }, enabled = state.result != ExecutionResultState.Sending && state.pending == null) { Text("Подтвердить") } },
            dismissButton = { TextButton(onClick = { onIntent(DispatchExecutionIntent.DismissReview) }, enabled = state.result != ExecutionResultState.Sending) { Text("Изменить") } })
    }
}
