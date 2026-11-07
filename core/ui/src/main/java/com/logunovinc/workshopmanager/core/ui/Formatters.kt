package com.logunovinc.workshopmanager.core.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.logunovinc.workshopmanager.domain.task.*

object QuantityFormatter {
    fun format(value: BigDecimal): String = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')
}
fun localDate(value: Instant?): String = value?.let { DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(it) } ?: "—"
fun localDay(value: Instant?): String = value?.let { DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault()).format(it) } ?: "—"
fun WorkshopRequestStage.title(): String = when (this) {
    WorkshopRequestStage.NEW -> "Новая"
    WorkshopRequestStage.IN_PROGRESS -> "В работе"
    WorkshopRequestStage.CANCELLED -> "Отменена"
    WorkshopRequestStage.SERVER_COMPLETED -> "Завершена на сервере"
    WorkshopRequestStage.SERVER_CLOSED_INCOMPLETE -> "Закрыта на сервере не полностью"
    WorkshopRequestStage.UNSUPPORTED -> "Статус неизвестен"
}
fun TaskType.title(): String = when (this) {
    TaskType.RECEIVE_GOODS -> "Приёмка"
    TaskType.DISPATCH_GOODS -> "Отправка"
    TaskType.MANUFACTURE_PRODUCT -> "Изготовление"
    TaskType.UNKNOWN -> "Неизвестный тип задания"
}
