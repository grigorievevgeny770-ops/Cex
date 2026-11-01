package com.logunovinc.workshopmanager.domain.task

import java.math.BigDecimal
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.pending.CommandOutcome

data class ActualLineInput(val taskLineId: Long, val quantity: String = "", val sheets: String = "")
data class ReceiveDraft(val lines: List<ActualLineInput>, val final: Boolean = false, val reason: String = "", val comment: String = "")
/** Each quantity is the delta for this fact, never the cumulative accepted amount. */
data class ActualLine(val taskLineId: Long, val quantity: BigDecimal? = null, val sheets: Int? = null)
data class ReceiveCommand(val lines: List<ActualLine>, val final: Boolean, val reason: String, val comment: String, val shortage: Boolean)

object QuantityInputParser {
    fun quantity(raw: String): BigDecimal? {
        val normalized = raw.trim().replace(',', '.')
        if (normalized.length > 64 || !normalized.matches(Regex("[0-9]+(?:[.][0-9]{1,4})?"))) return null
        return normalized.toBigDecimalOrNull()
    }
    fun sheets(raw: String): Int? = raw.trim().takeIf { it.length <= 64 && it.matches(Regex("[0-9]+")) }?.toIntOrNull()
}
object ReceiveValidator {
    fun validate(task: Task, draft: ReceiveDraft): Outcome<ReceiveCommand> {
        fun invalid(message: String): Outcome.Failure = Outcome.Failure(AppError(ErrorKind.VALIDATION, message))
        if (task.type != TaskType.RECEIVE_GOODS || !task.canExecute) return Outcome.Failure(AppError(ErrorKind.FORBIDDEN, "Приёмка этого задания сейчас недоступна"))
        if (task.version <= 0 || task.transferVersion == null || task.transferVersion <= 0) return invalid("Нет актуальной версии перемещения. Обновите карточку")
        if (draft.comment.length > 2000 || draft.reason.length > 2000) return invalid("Комментарий и причина должны быть не длиннее 2000 символов")
        if (draft.lines.size > 500 || draft.lines.map { it.taskLineId }.distinct().size != draft.lines.size) return invalid("Строки приёмки не должны повторяться")
        val byId = task.lines.associateBy(TaskLine::id)
        val actual = mutableListOf<ActualLine>()
        for (input in draft.lines) {
            val line = byId[input.taskLineId] ?: return invalid("Строка №${input.taskLineId} не принадлежит заданию")
            val sheetBased = line.plannedSheets != null || line.remainingSheets != null
            if (sheetBased) {
                if (input.quantity.isNotBlank()) return invalid("Для ${line.productName} укажите только фактическое число листов")
                if (input.sheets.isBlank()) continue
                val sheets = QuantityInputParser.sheets(input.sheets) ?: return invalid("Для ${line.productName} нужно целое число листов от 0 до 2147483647")
                val remaining = line.remainingSheets ?: return invalid("Неизвестен остаток листов ${line.productName}. Обновите карточку")
                if (sheets > remaining) return invalid("Для ${line.productName} осталось $remaining листов")
                if (sheets > 0) actual += ActualLine(line.id, sheets = sheets)
            } else {
                if (input.sheets.isNotBlank()) return invalid("Для ${line.productName} план задан в базовом количестве, без листов")
                if (input.quantity.isBlank()) continue
                val quantity = QuantityInputParser.quantity(input.quantity) ?: return invalid("Для ${line.productName} нужно неотрицательное количество, не более 4 знаков после запятой")
                if (quantity > line.remaining) return invalid("Количество ${line.productName} превышает остаток задания")
                if (quantity.signum() > 0) actual += ActualLine(line.id, quantity = quantity)
            }
        }
        if (!draft.final && actual.isEmpty()) return invalid("Для частичной приёмки укажите реально прибывшее количество")
        val quantities = actual.associateBy(ActualLine::taskLineId)
        val shortage = task.lines.any { line ->
            if (line.plannedSheets != null || line.remainingSheets != null) (quantities[line.id]?.sheets ?: 0) < (line.remainingSheets ?: 0)
            else (quantities[line.id]?.quantity ?: BigDecimal.ZERO) < line.remaining
        }
        if (draft.final && shortage && draft.reason.trim().count(Char::isLetterOrDigit) < 3) return invalid("Укажите содержательную причину окончательной недостачи")
        return Outcome.Success(ReceiveCommand(actual.toList(), draft.final, draft.reason.trim(), draft.comment, shortage))
    }
}
/** Validates the immutable review summary without performing a stock mutation. */
class PrepareReceiveUseCase {
    operator fun invoke(task: Task, draft: ReceiveDraft): Outcome<ReceiveCommand> = ReceiveValidator.validate(task, draft)
}
/** Revalidates a confirmed step and delegates exact persisted execution to the repository. */
class ExecuteReceiveUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task, draft: ReceiveDraft): CommandOutcome = when (val prepared = ReceiveValidator.validate(task, draft)) {
        is Outcome.Failure -> CommandOutcome.Rejected(null, prepared.error)
        is Outcome.Success -> repository.receive(task, prepared.value)
    }
}
