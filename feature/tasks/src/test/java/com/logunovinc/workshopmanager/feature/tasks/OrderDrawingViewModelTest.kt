package com.logunovinc.workshopmanager.feature.tasks

import androidx.lifecycle.SavedStateHandle
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.order.*
import com.logunovinc.workshopmanager.domain.pending.*
import com.logunovinc.workshopmanager.domain.stock.*
import com.logunovinc.workshopmanager.domain.task.Page
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class OrderDrawingViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){Dispatchers.resetMain()}
    private class Orders : WorkshopOrderRepository {
        val row=WorkshopOrder(7,3,OrderStatus.IN_PROGRESS,"in_progress",null,"",listOf(OrderLine(1,501,"Уголок","2000",BigDecimal.ONE,"шт",false,2)),setOf("manufacture_area"))
        val drawing=ProfileDrawing(7,1,2,false,2000,110,listOf(ProfileSegment(100,null)),emptyList(),emptyList(),ProfileEdge("hem","in"),ProfileEdge("none","in"))
        var readDrawing:Outcome<ProfileDrawing> = Outcome.Success(drawing)
        val preview=AreaPreview(7,3,"a".repeat(64),BigDecimal("0.2200"),listOf(AreaPreviewLine(1,2,"Уголок",110,2000,BigDecimal.ONE,BigDecimal("0.2200"))),listOf(AreaPreviewMaterial(900,"Плоский лист",BigDecimal("0.2200"))))
        var readPreview:Outcome<AreaPreview> = Outcome.Success(preview)
        var sentHash:String?=null
        var writes=0
        val release=CompletableDeferred<Unit>()
        override suspend fun drawing(requestId:Long,lineId:Long)=readDrawing
        override suspend fun order(id:Long)=Outcome.Success(row)
        override suspend fun areaPreview(requestId:Long)=readPreview
        override suspend fun manufactureArea(order:WorkshopOrder,previewHash:String?):CommandOutcome {writes++;sentHash=previewHash;release.await();return CommandOutcome.Confirmed(CommandConfirmation("area",7,4,null,false))}
        override suspend fun contracts():Outcome<WorkshopContracts> = error("unused")
        override suspend fun orders(warehouseId:Long,view:OrderView,limit:Int,offset:Long):Outcome<Page<WorkshopOrder>> = error("unused")
        override suspend fun start(order:WorkshopOrder):CommandOutcome = error("unused")
        override suspend fun manufacture(order:WorkshopOrder,materials:List<SheetUse>):CommandOutcome = error("unused")
        override suspend fun handOver(order:WorkshopOrder):CommandOutcome = error("unused")
        override suspend fun reverse(order:WorkshopOrder,target:ReverseTarget):CommandOutcome = error("unused")
        override suspend fun stockSummary(warehouseId:Long,on:LocalDate):Outcome<WorkshopStockSummary> = error("unused")
        override suspend fun sheetRequests(warehouseId:Long):Outcome<List<SheetRequest>> = error("unused")
        override suspend fun requestSheet(warehouseId:Long,productId:Long,length:BigDecimal,sheets:Int):CommandOutcome = error("unused")
        override suspend fun receiveSheet(request:SheetRequest):CommandOutcome = error("unused")
    }
    private val stock=object:StockRepository {
        override suspend fun stock(warehouseId:Long,filter:StockFilter,limit:Int,offset:Long):Outcome<Page<StockPosition>> = error("unused")
        override suspend fun position(warehouseId:Long,productId:Long):Outcome<StockPosition> = error("unused")
        override suspend fun products(query:String,limit:Int,offset:Long):Outcome<Page<Product>> = error("unused")
        override suspend fun product(productId:Long):Outcome<Product> = error("unused")
        override suspend fun definition(productId:Long):Outcome<StockDefinition> = error("unused")
    }
    private val pending=object:PendingCommandRepository {
        override fun observe()=MutableStateFlow(emptyList<PendingCommand>())
        override suspend fun retry(commandId:String):CommandOutcome = error("unused")
    }
    private fun vm(repo:Orders)=OrderDetailViewModel(SavedStateHandle(mapOf("orderId" to 7L)),WorkshopOrders(repo),SearchProductsUseCase(stock),LoadProductUseCase(stock),pending,RetryPendingCommandUseCase(pending))
    @Test fun failedOrChangedDrawingBlocksManufacture()=runTest(dispatcher){
        for(result in listOf(Outcome.Failure(AppError(ErrorKind.NETWORK,"Нет связи")),Outcome.Success(Orders().drawing.copy(version=3)))){
            val repo=Orders().apply{readDrawing=result};val vm=vm(repo);runCurrent()
            vm.onIntent(OrderDetailIntent.ManufactureArea);vm.onIntent(OrderDetailIntent.Confirm);runCurrent()
            assertNull(vm.uiState.value.confirm);assertEquals(0,repo.writes)
            assertTrue(vm.uiState.value.drawingErrors.containsKey(1L))
        }
    }
    @Test fun matchingDrawingRequiresConfirmationAndDoubleTapSendsOnce()=runTest(dispatcher){
        val repo=Orders();val vm=vm(repo);runCurrent()
        assertEquals(2L,vm.uiState.value.drawings[1]!!.version)
        vm.onIntent(OrderDetailIntent.ManufactureArea);assertEquals(0,repo.writes)
        assertEquals(OrderConfirm.ManufactureArea,vm.uiState.value.confirm)
        vm.onIntent(OrderDetailIntent.Confirm);vm.onIntent(OrderDetailIntent.Confirm);runCurrent()
        assertEquals(1,repo.writes);assertTrue(vm.uiState.value.sending)
        assertEquals(repo.preview.hash,repo.sentHash)
        repo.release.complete(Unit);runCurrent();assertFalse(vm.uiState.value.sending)
    }
    @Test fun missingOrStaleAreaPreviewBlocksConfirmation()=runTest(dispatcher){
        val sample=Orders()
        for(result in listOf(Outcome.Failure(AppError(ErrorKind.NETWORK,"Расход не загружен")),
            Outcome.Success(sample.preview.copy(requestVersion=4)),
            Outcome.Success(sample.preview.copy(lines=sample.preview.lines.map { it.copy(drawingVersion=3) })))) {
            val repo=Orders().apply{readPreview=result};val vm=vm(repo);runCurrent()
            vm.onIntent(OrderDetailIntent.ManufactureArea);vm.onIntent(OrderDetailIntent.Confirm);runCurrent()
            assertNull(vm.uiState.value.confirm);assertEquals(0,repo.writes);assertNotNull(vm.uiState.value.previewError)
        }
    }
}
