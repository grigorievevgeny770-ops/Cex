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
fun TaskType.title(): String = when (this) {
    TaskType.RECEIVE_GOODS -> "Приёмка"
    TaskType.DISPATCH_GOODS -> "Отправка"
    TaskType.MANUFACTURE_PRODUCT -> "Изготовление"
    TaskType.UNKNOWN -> "Неизвестный тип задания"
}
fun TaskStatus.title(): String = when (this) {
    TaskStatus.NEW -> "Новое"
    TaskStatus.IN_PROGRESS -> "В работе"
    TaskStatus.PARTIALLY_COMPLETED -> "Выполнено частично"
    TaskStatus.WAITING_RESOLUTION -> "Ожидает решения"
    TaskStatus.COMPLETED -> "Завершено"
    TaskStatus.CANCELLED -> "Отменено"
    TaskStatus.UNKNOWN -> "Неизвестный статус"
}
