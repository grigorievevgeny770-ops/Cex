package com.logunovinc.workshopmanager.domain.order

import java.math.BigDecimal
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.pending.CommandOutcome

object OrderCommands {
    fun manufacture(order: WorkshopOrder, materials: List<SheetUse>): Outcome<List<SheetUse>> {
        if (!order.canManufacture) return denied("Изготовление этой заявки сейчас недоступно")
        if (order.version <= 0) return denied("Обновите заявку")
        if (order.normUnspecified) return denied("Сервер не сообщил, задана ли норма. Расход не отправляем")
        if (!order.needsActualSheet) {
            if (materials.isNotEmpty()) return denied("Норма уже на сервере. Фактический расход вводить нельзя")
            return Outcome.Success(emptyList())
        }
        if (materials.isEmpty() || materials.size > 20) return denied("Укажите фактический расход плоского листа")
        val seen = mutableSetOf<Pair<Long, String>>()
        for (material in materials) {
            if (material.productId <= 0 || material.sheets !in 1..2_147_483_647) return denied("Укажите лист и целое число листов")
            if (material.length.signum() <= 0 || material.length >= BigDecimal("10000000") || material.length.scale() > 3) return denied("Длина листа должна быть положительной, не больше 3 знаков")
            if (!seen.add(material.productId to material.length.stripTrailingZeros().toPlainString())) return denied("Один лист и одна длина не должны повторяться")
        }
        return Outcome.Success(materials)
    }
    private fun denied(message: String) = Outcome.Failure(AppError(ErrorKind.VALIDATION, message))
}

class WorkshopOrders(private val repository: WorkshopOrderRepository) {
    suspend fun contracts() = repository.contracts()
    suspend fun orders(warehouseId: Long, view: OrderView, offset: Long = 0) = repository.orders(warehouseId, view, 50, offset)
    suspend fun order(id: Long) = repository.order(id)
    suspend fun start(order: WorkshopOrder): CommandOutcome = if (order.canStart && order.version > 0) repository.start(order)
        else CommandOutcome.Rejected(null, AppError(ErrorKind.FORBIDDEN, "Взять заявку в работу сейчас нельзя"))
    suspend fun manufacture(order: WorkshopOrder, materials: List<SheetUse>): CommandOutcome = when (val ready = OrderCommands.manufacture(order, materials)) {
        is Outcome.Failure -> CommandOutcome.Rejected(null, ready.error)
        is Outcome.Success -> repository.manufacture(order, ready.value)
    }
    suspend fun handOver(order: WorkshopOrder): CommandOutcome = if (order.canHandOver && order.version > 0) repository.handOver(order)
        else CommandOutcome.Rejected(null, AppError(ErrorKind.FORBIDDEN, "Передать заявку на склад сейчас нельзя"))
    suspend fun reverse(order: WorkshopOrder, target: ReverseTarget): CommandOutcome {
        val allowed = when (target) {
            ReverseTarget.MANUFACTURE -> order.canReverseManufacture
            ReverseTarget.HAND_OVER -> order.canReverseHandOver
        }
        return if (allowed && order.version > 0) repository.reverse(order, target)
            else CommandOutcome.Rejected(null, AppError(ErrorKind.FORBIDDEN, "Отмена этого шага сейчас недоступна"))
    }
    suspend fun stockSummary(warehouseId: Long, on: java.time.LocalDate) = repository.stockSummary(warehouseId, on)
    suspend fun sheetRequests(warehouseId: Long) = repository.sheetRequests(warehouseId)
    suspend fun requestSheet(warehouseId: Long, productId: Long, length: BigDecimal, sheets: Int) = repository.requestSheet(warehouseId, productId, length, sheets)
    suspend fun receiveSheet(request: SheetRequest) = if (request.canReceive) repository.receiveSheet(request)
        else CommandOutcome.Rejected(null, AppError(ErrorKind.FORBIDDEN, "Принять этот лист сейчас нельзя"))
}
