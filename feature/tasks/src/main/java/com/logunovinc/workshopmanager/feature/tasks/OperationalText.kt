package com.logunovinc.workshopmanager.feature.tasks

import com.logunovinc.workshopmanager.domain.AppError
import com.logunovinc.workshopmanager.domain.pending.CommandSummary

/** Presentation of known generated journal labels; the persisted request is never changed. */
fun CommandSummary.operationalTitle(): String = title.replace(Regex(" · задание №[0-9]+$"), "")
fun CommandSummary.operationalLines(): List<String> = lines.filterNot { it.matches(Regex("Версия (?:задания|нормы) [0-9]+(?:, (?:нормы|перемещения) [0-9]+)?")) }
    .map { it.replace(Regex(" · строка №[0-9]+"), "") }
fun AppError.operationalMessage(): String = when(code) {
    "DOCUMENT_STATE_CONFLICT", "RECIPE_VERSION_CONFLICT" -> "Задание изменилось. Обновите данные и проверьте количество"
    "IDEMPOTENCY_CONFLICT" -> "Не удалось сверить действие. Откройте неподтверждённые действия"
    else -> message
}
