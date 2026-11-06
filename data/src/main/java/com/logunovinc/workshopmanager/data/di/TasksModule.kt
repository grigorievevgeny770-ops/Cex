package com.logunovinc.workshopmanager.data.di

import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.data.repository.*
import com.logunovinc.workshopmanager.domain.order.WorkshopOrderRepository
import com.logunovinc.workshopmanager.domain.order.WorkshopOrders
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.production.*
import com.logunovinc.workshopmanager.domain.warehouse.*
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module @InstallIn(SingletonComponent::class)
object TasksModule {
    @Provides @Singleton fun remote(factory: ApiFactory, current: CurrentSession, errors: ApiErrorMapper) = WorkshopRemoteDataSource(factory, current, errors)
    @Provides @Singleton fun mapper() = TaskMapper()
    @Provides @Singleton fun warehouses(remote: WorkshopRemoteDataSource): WarehouseRepository = RealWarehouseRepository(remote)
    @Provides @Singleton fun tasks(remote: WorkshopRemoteDataSource, mapper: TaskMapper, dispatcher: CommandDispatcher, json: Json, errors: ApiErrorMapper): TaskRepository = RealTaskRepository(remote, mapper, dispatcher, json, errors)
    @Provides fun closeIncomplete(repository: TaskRepository) = CloseIncompleteTaskUseCase(repository)
    @Provides fun closeCapability(repository: TaskRepository) = CheckCloseIncompleteCapabilityUseCase(repository)
    @Provides fun prepareManufacturing() = PrepareManufacturingUseCase()
    @Provides fun previewManufacturing(repository: TaskRepository) = PreviewManufacturingUseCase(repository)
    @Provides fun executeManufacturing(repository: TaskRepository) = ExecuteManufacturingUseCase(repository)
    @Provides fun prepareDispatch() = PrepareDispatchUseCase()
    @Provides fun dispatch(repository: TaskRepository) = ExecuteDispatchUseCase(repository)
    @Provides fun prepareReceive() = PrepareReceiveUseCase()
    @Provides fun receive(repository: TaskRepository) = ExecuteReceiveUseCase(repository)
    @Provides @Singleton fun orders(remote: WorkshopRemoteDataSource, dispatcher: CommandDispatcher, json: Json, errors: ApiErrorMapper): WorkshopOrderRepository =
        RealWorkshopOrderRepository(remote, dispatcher, json, errors)
    @Provides fun workshopOrders(repository: WorkshopOrderRepository) = WorkshopOrders(repository)
    @Provides fun resolve(repository: WarehouseRepository) = ResolveWorkshopUseCase(repository)
    @Provides fun loadTasks(repository: TaskRepository) = LoadTasksUseCase(repository)
    @Provides fun loadTask(repository: TaskRepository) = LoadTaskUseCase(repository)
    @Provides fun executions(repository: TaskRepository) = LoadExecutionsUseCase(repository)
    @Provides fun history(repository: TaskRepository) = LoadHistoryUseCase(repository)
}
