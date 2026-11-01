package com.logunovinc.workshopmanager.domain.task

import com.logunovinc.workshopmanager.domain.*

/** Validates frozen task-line physical deltas without reading current product geometry. */
object ActualLinesValidator {
    fun validate(task: Task, inputs: List<ActualLineInput>): Outcome<List<ActualLine>> {
        fun invalid(message: String): Outcome.Failure = Outcome.Failure(AppError(ErrorKind.VALIDATION, message))
        if (inputs.size > 500 || inputs.map { it.taskLineId }.distinct().size != inputs.size) return invalid("Строки факта не должны повторяться")
        val byId = task.lines.associateBy(TaskLine::id)
        val actual = mutableListOf<ActualLine>()
        for (input in inputs) {
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
        return Outcome.Success(actual.toList())
    }
}
