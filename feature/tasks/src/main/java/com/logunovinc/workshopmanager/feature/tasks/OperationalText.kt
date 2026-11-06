package com.logunovinc.workshopmanager.feature.tasks

import com.logunovinc.workshopmanager.core.ui.QuantityFormatter
import com.logunovinc.workshopmanager.domain.AppError
import com.logunovinc.workshopmanager.domain.pending.CommandSummary

/** Presentation of known generated journal labels; the persisted request is never changed. */
fun CommandSummary.operationalTitle(): String = title.replace(Regex(" · задание №[0-9]+$"), "")
fun CommandSummary.operationalLines(): List<String> = lines.filterNot { it.matches(Regex("Версия (?:задания|нормы) [0-9]+(?:, (?:нормы|перемещения) [0-9]+)?")) }
    .map { line -> formatSummaryLine(line).replace(Regex(" · строка №[0-9]+"), "") }

private val summaryDecimal = "(-?[0-9]+(?:\\.[0-9]+)?)"
private fun CommandSummary.formatSummaryLine(line: String): String {
    if (line.startsWith("Причина:")) return line
    fun formatted(raw: String) = raw.toBigDecimalOrNull()?.let(QuantityFormatter::format) ?: raw
    // Only generated quantity suffixes; product names, notes and IDs stay untouched.
    if (title.startsWith("Закрыть остаток · задание №")) {
        val match = Regex("^(.*: план )$summaryDecimal(, факт )$summaryDecimal(, остаток )$summaryDecimal( .*)$").matchEntire(line)
        if (match != null) return match.groupValues.let { it[1] + formatted(it[2]) + it[3] + formatted(it[4]) + it[5] + formatted(it[6]) + it[7] }
    }
    val generatedFact = Regex("^(.* · строка №[0-9]+: )$summaryDecimal( .*)$")
    val generatedProduction = Regex("^((?:Будет списано|Будет изготовлено) .*: )$summaryDecimal( .*)$")
    val match = when {
        title.startsWith("Отправка · задание №") || title.startsWith("Окончательная приёмка · задание №") || title.startsWith("Частичная приёмка — продолжить ожидание · задание №") -> generatedFact.matchEntire(line)
        title.startsWith("Первый прототип · задание №") || title.startsWith("Выпуск по норме · задание №") -> generatedProduction.matchEntire(line)
        else -> null
    } ?: return line
    // Sheet counts are integers, not decimal quantities.
    return if (match.groupValues[3] == " листов") line else match.groupValues[1] + formatted(match.groupValues[2]) + match.groupValues[3]
}
fun AppError.operationalMessage(): String = when(code) {
    "DOCUMENT_STATE_CONFLICT", "RECIPE_VERSION_CONFLICT" -> "Задание изменилось. Обновите данные и проверьте количество"
    "IDEMPOTENCY_CONFLICT" -> "Нужна сверка сохранённого действия. Не выполняйте заново"
    else -> message
}
