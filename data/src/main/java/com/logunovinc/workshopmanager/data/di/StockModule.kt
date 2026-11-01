package com.logunovinc.workshopmanager.data.di

import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.data.repository.RealStockRepository
import com.logunovinc.workshopmanager.domain.stock.*
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object StockModule {
    @Provides @Singleton fun calls(factory: ApiFactory, current: CurrentSession, errors: ApiErrorMapper) = AuthenticatedRequestExecutor(factory, current, errors)
    @Provides @Singleton fun remote(calls: AuthenticatedRequestExecutor) = StockRemoteDataSource(calls)
    @Provides @Singleton fun repository(remote: StockRemoteDataSource, errors: ApiErrorMapper): StockRepository = RealStockRepository(remote, errors)
    @Provides fun stock(repository: StockRepository) = LoadStockUseCase(repository)
    @Provides fun position(repository: StockRepository) = LoadStockPositionUseCase(repository)
    @Provides fun products(repository: StockRepository) = SearchProductsUseCase(repository)
    @Provides fun definition(repository: StockRepository) = LoadStockDefinitionUseCase(repository)
    @Provides fun product(repository: StockRepository) = LoadProductUseCase(repository)
}
