package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.remote.ApiErrorMapper
import com.logunovinc.workshopmanager.data.remote.WorkshopRemoteDataSource
import com.logunovinc.workshopmanager.data.remote.dto.*
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.order.*
import com.logunovinc.workshopmanager.domain.pending.*
import com.logunovinc.workshopmanager.domain.task.Page
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RealWorkshopOrderRepository(
    private val remote: WorkshopRemoteDataSource,
    private val dispatcher: CommandDispatcher,
    private val json: Json,
    private val errors: ApiErrorMapper,
) : WorkshopOrderRepository {
    private fun <A, B> map(result: Outcome<A>, block: (A) -> B): Outcome<B> = when (result) {
        is Outcome.Failure -> result
        is Outcome.Success -> try { Outcome.Success(block(result.value)) } catch (error: Exception) { Outcome.Failure(errors.throwable(error)) }
    }
    private fun instant(value: String) = OffsetDateTime.parse(value).toInstant()
    private fun status(value: String) = when (value) {
        "new" -> OrderStatus.NEW
        "in_progress" -> OrderStatus.IN_PROGRESS
        "manufactured" -> OrderStatus.MANUFACTURED
        "handed_to_warehouse" -> OrderStatus.HANDED_TO_WAREHOUSE
        "cancelled" -> OrderStatus.CANCELLED
        else -> OrderStatus.UNKNOWN
    }
    private fun order(value: WorkshopOrderDto) = WorkshopOrder(
        value.id, value.version, status(value.status), value.status, value.dueAt?.let(::instant), value.comment,
        value.lines.map { OrderLine(it.id, it.productId, it.productName, it.size, it.quantity, it.unit, it.normKnown) },
        value.allowedActions.toSet(),
    )
    private fun line(value: StockSummaryLineDto) = StockSummaryLine(value.productId, value.productName, value.size, value.quantity, value.unit, value.sheets)
    private fun sheetStatus(value: String) = when (value) {
        "requested" -> SheetRequestStatus.REQUESTED
        "confirmed" -> SheetRequestStatus.CONFIRMED
        "received" -> SheetRequestStatus.RECEIVED
        "cancelled" -> SheetRequestStatus.CANCELLED
        else -> SheetRequestStatus.UNKNOWN
    }
    private fun sheet(value: SheetRequestDto) = SheetRequest(value.id, value.version, value.productId, value.productName, value.length, value.sheets, sheetStatus(value.status), value.allowedActions.toSet())
    override suspend fun contracts() = remote.workshopContracts()
    override suspend fun sheetCatalog() = map(remote.sheetCatalog()) { list -> list.map { com.logunovinc.workshopmanager.domain.stock.Product(it.id, it.name, it.baseUnit, it.sheet, it.sku) } }
    override suspend fun orders(warehouseId: Long, view: OrderView, limit: Int, offset: Long) = map(remote.orders(warehouseId, if (view == OrderView.HISTORY) "history" else "active", limit, offset)) {
        Page(it.items.map(::order), it.total, it.limit, it.offset)
    }
    override suspend fun order(id: Long) = map(remote.order(id), ::order)
    private suspend fun command(order: WorkshopOrder, action: String, path: String, title: String, body: (String) -> ByteArray): CommandOutcome {
        if (order.status == OrderStatus.UNKNOWN) return CommandOutcome.Rejected(null, AppError(ErrorKind.INCOMPATIBLE_SERVER, "Неизвестный статус заявки"))
        val profile = remote.sessionSnapshot() ?: return CommandOutcome.Rejected(null, AppError(ErrorKind.AUTHENTICATION, "Требуется вход"))
        val summary = CommandSummary(title, order.lines.map { "${it.productName}: ${it.quantity.toPlainString()} ${it.unit}" })
        return dispatcher.submit(order.id, action, "POST", path, summary, expectedSession = profile, body = body)
    }
    override suspend fun start(order: WorkshopOrder) = command(order, "REQUEST_START", "api/workshop/requests/${order.id}/start", "Взять в работу") { token ->
        json.encodeToString(OrderCommandDto(order.version, token)).toByteArray()
    }
    override suspend fun manufacture(order: WorkshopOrder, materials: List<SheetUse>) = command(order, "REQUEST_MANUFACTURE", "api/workshop/requests/${order.id}/manufacture", "Изготовлено") { token ->
        val bytes = if (materials.isEmpty()) json.encodeToString(OrderCommandDto(order.version, token))
            else json.encodeToString(ManufactureCommandDto(order.version, token, materials.map { SheetUseDto(it.productId, it.sheets, it.length) }))
        bytes.toByteArray()
    }
    override suspend fun handOver(order: WorkshopOrder) = command(order, "REQUEST_HAND_OVER", "api/workshop/requests/${order.id}/hand-over", "Передано на склад") { token ->
        json.encodeToString(OrderCommandDto(order.version, token)).toByteArray()
    }
    override suspend fun reverse(order: WorkshopOrder, target: ReverseTarget) = command(order, "REQUEST_REVERSE", "api/workshop/requests/${order.id}/reverse", "Отмена шага") { token ->
        json.encodeToString(ReverseCommandDto(order.version, token, if (target == ReverseTarget.MANUFACTURE) "manufacture" else "hand_over")).toByteArray()
    }
    override suspend fun stockSummary(warehouseId: Long, on: LocalDate) = map(remote.stockSummary(warehouseId, on.toString())) {
        WorkshopStockSummary(it.flatSheet.map(::line), it.awaitingTransfer.map(::line), it.transferredOn.map(::line))
    }
    override suspend fun sheetRequests(warehouseId: Long) = map(remote.sheetRequests(warehouseId)) { it.map(::sheet) }
    override suspend fun requestSheet(warehouseId: Long, productId: Long, length: java.math.BigDecimal, sheets: Int): CommandOutcome {
        if (warehouseId <= 0 || productId <= 0 || sheets !in 1..2_147_483_647 || length.signum() <= 0) return CommandOutcome.Rejected(null, AppError(ErrorKind.VALIDATION, "Укажите плоский лист, длину и число листов"))
        val profile = remote.sessionSnapshot() ?: return CommandOutcome.Rejected(null, AppError(ErrorKind.AUTHENTICATION, "Требуется вход"))
        val summary = CommandSummary("Запрос листа", listOf("$sheets листов, длина ${length.toPlainString()} м"))
        return dispatcher.submit(productId, "SHEET_REQUEST", "POST", "api/workshop/sheet-requests", summary, expectedSession = profile) { token ->
            json.encodeToString(SheetRequestCreateDto(warehouseId, productId, sheets, length, token)).toByteArray()
        }
    }
    override suspend fun receiveSheet(request: SheetRequest): CommandOutcome {
        val profile = remote.sessionSnapshot() ?: return CommandOutcome.Rejected(null, AppError(ErrorKind.AUTHENTICATION, "Требуется вход"))
        return dispatcher.submit(request.id, "SHEET_RECEIVE", "POST", "api/workshop/sheet-requests/${request.id}/receive", CommandSummary("Принять лист", listOf(request.productName)), expectedSession = profile) { token ->
            json.encodeToString(OrderCommandDto(request.version, token)).toByteArray()
        }
    }
}
