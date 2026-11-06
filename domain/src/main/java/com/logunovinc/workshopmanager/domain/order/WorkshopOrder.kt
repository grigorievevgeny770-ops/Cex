package com.logunovinc.workshopmanager.domain.order

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import com.logunovinc.workshopmanager.domain.Outcome
import com.logunovinc.workshopmanager.domain.pending.CommandOutcome
import com.logunovinc.workshopmanager.domain.task.Page

enum class OrderStatus { NEW, IN_PROGRESS, MANUFACTURED, HANDED_TO_WAREHOUSE, CANCELLED, UNKNOWN }
enum class OrderView { ACTIVE, HISTORY }
enum class ReverseTarget { MANUFACTURE, HAND_OVER }
enum class SheetRequestStatus { REQUESTED, CONFIRMED, RECEIVED, CANCELLED, UNKNOWN }

data class WorkshopContracts(val orders: Boolean, val stockSummary: Boolean, val sheetRequests: Boolean)

data class OrderLine(
    val id: Long, val productId: Long, val productName: String, val size: String?,
    val quantity: BigDecimal, val unit: String, val normKnown: Boolean?,
)

data class WorkshopOrder(
    val id: Long, val version: Long, val status: OrderStatus, val rawStatus: String,
    val dueAt: Instant?, val comment: String, val lines: List<OrderLine>, val allowedActions: Set<String>,
) {
    val canStart: Boolean get() = status == OrderStatus.NEW && "start" in allowedActions
    val canManufacture: Boolean get() = status == OrderStatus.IN_PROGRESS && "manufacture" in allowedActions
    val canHandOver: Boolean get() = status == OrderStatus.MANUFACTURED && "hand_over" in allowedActions
    val canReverseManufacture: Boolean get() = status == OrderStatus.MANUFACTURED && "reverse_manufacture" in allowedActions
    val canReverseHandOver: Boolean get() = status == OrderStatus.HANDED_TO_WAREHOUSE && "reverse_hand_over" in allowedActions
    val needsActualSheet: Boolean get() = lines.any { it.normKnown == false }
    val normUnspecified: Boolean get() = lines.any { it.normKnown == null }
}

/** Actual flat-sheet use. Present only when the server says the norm is unknown. */
data class SheetUse(val productId: Long, val sheets: Int, val length: BigDecimal)

data class StockSummaryLine(
    val productId: Long, val productName: String, val size: String?,
    val quantity: BigDecimal, val unit: String, val sheets: Int?,
)
data class WorkshopStockSummary(
    val flatSheet: List<StockSummaryLine>,
    val awaitingTransfer: List<StockSummaryLine>,
    val transferredOn: List<StockSummaryLine>,
)

data class SheetRequest(
    val id: Long, val version: Long, val productId: Long, val productName: String,
    val length: BigDecimal, val sheets: Int, val status: SheetRequestStatus, val allowedActions: Set<String>,
) {
    val canReceive: Boolean get() = status == SheetRequestStatus.CONFIRMED && "receive" in allowedActions
}

interface WorkshopOrderRepository {
    suspend fun contracts(): Outcome<WorkshopContracts>
    suspend fun orders(warehouseId: Long, view: OrderView, limit: Int = 50, offset: Long = 0): Outcome<Page<WorkshopOrder>>
    suspend fun order(id: Long): Outcome<WorkshopOrder>
    suspend fun start(order: WorkshopOrder): CommandOutcome
    suspend fun manufacture(order: WorkshopOrder, materials: List<SheetUse>): CommandOutcome
    suspend fun handOver(order: WorkshopOrder): CommandOutcome
    suspend fun reverse(order: WorkshopOrder, target: ReverseTarget): CommandOutcome
    suspend fun stockSummary(warehouseId: Long, on: LocalDate): Outcome<WorkshopStockSummary>
    suspend fun sheetRequests(warehouseId: Long): Outcome<List<SheetRequest>>
    suspend fun requestSheet(warehouseId: Long, productId: Long, length: BigDecimal, sheets: Int): CommandOutcome
    suspend fun receiveSheet(request: SheetRequest): CommandOutcome
}
