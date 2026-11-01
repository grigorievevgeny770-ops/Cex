package com.logunovinc.workshopmanager.di

import android.content.Context
import com.logunovinc.workshopmanager.BuildConfig
import com.logunovinc.workshopmanager.feature.auth.AuthPrefill
import com.logunovinc.workshopmanager.data.local.*
import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.data.repository.RealAuthRepository
import com.logunovinc.workshopmanager.domain.auth.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object ApplicationModule {
    private val DEFAULT_URL = BuildConfig.DEFAULT_BASE_URL
    @Provides @Singleton fun json(): Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    @Provides @Singleton fun cookieStorage(@ApplicationContext context: Context): CookieStorage = EncryptedCookieStorage(context)
    @Provides @Singleton fun cookieJar(storage: CookieStorage) = PersistentSessionCookieJar(storage)
    @Provides @Singleton fun apiFactory(json: Json, jar: PersistentSessionCookieJar) = ApiFactory(json, jar)
    @Provides @Singleton fun errors(json: Json) = ApiErrorMapper(json)
    @Provides @Singleton fun environment(@ApplicationContext context: Context): EnvironmentSettings = DataStoreEnvironmentSettings(context, DEFAULT_URL)
    @Provides @Singleton fun realAuth(settings: EnvironmentSettings, factory: ApiFactory, errors: ApiErrorMapper): RealAuthRepository =
        RealAuthRepository(settings, factory, errors, BuildConfig.DEBUG, DEFAULT_URL)
    @Provides @Singleton fun auth(repository: RealAuthRepository): AuthRepository = repository
    @Provides @Singleton fun currentSession(repository: RealAuthRepository): CurrentSession = repository
    @Provides fun authPrefill() = AuthPrefill(BuildConfig.DEBUG_PREFILL_LOGIN, BuildConfig.DEBUG_PREFILL_PASSWORD)
    @Provides fun login(repository: AuthRepository) = LoginUseCase(repository)
    @Provides fun restore(repository: AuthRepository) = RestoreSessionUseCase(repository)
    @Provides fun logout(repository: AuthRepository) = LogoutUseCase(repository)
    @Provides fun environmentUseCase(repository: AuthRepository) = ChangeEnvironmentUseCase(repository)
}
