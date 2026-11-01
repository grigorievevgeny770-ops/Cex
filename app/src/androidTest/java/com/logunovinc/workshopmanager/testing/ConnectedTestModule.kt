package com.logunovinc.workshopmanager.testing

import android.content.Context
import androidx.room.Room
import com.logunovinc.workshopmanager.di.ApplicationModule
import com.logunovinc.workshopmanager.feature.auth.AuthPrefill
import com.logunovinc.workshopmanager.feature.tasks.TaskDebugOptions
import com.logunovinc.workshopmanager.data.di.PersistenceModule
import com.logunovinc.workshopmanager.data.local.*
import com.logunovinc.workshopmanager.data.local.pending.*
import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.data.repository.*
import com.logunovinc.workshopmanager.domain.auth.*
import com.logunovinc.workshopmanager.domain.pending.*
import dagger.*
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module @TestInstallIn(components = [SingletonComponent::class], replaces = [ApplicationModule::class, PersistenceModule::class])
object ConnectedTestModule {
    @Provides @Singleton fun json() = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    @Provides @Singleton fun storage(): CookieStorage = object : CookieStorage {
        private var value: String? = null
        @Synchronized override fun read() = value
        @Synchronized override fun write(value: String) { this.value = value }
    }
    @Provides @Singleton fun jar(storage: CookieStorage) = PersistentSessionCookieJar(storage)
    @Provides @Singleton fun factory(json: Json, jar: PersistentSessionCookieJar) = ApiFactory(json, jar)
    @Provides @Singleton fun errors(json: Json) = ApiErrorMapper(json)
    @Provides @Singleton fun settings(): EnvironmentSettings = object : EnvironmentSettings {
        private var url = FixtureRuntime.backend.baseUrl
        override suspend fun read() = url
        override suspend fun write(baseUrl: String) { url = baseUrl }
    }
    @Provides @Singleton fun realAuth(settings: EnvironmentSettings, factory: ApiFactory, errors: ApiErrorMapper) = RealAuthRepository(settings, factory, errors, true, FixtureRuntime.backend.baseUrl)
    @Provides @Singleton fun auth(repository: RealAuthRepository): AuthRepository = repository
    @Provides @Singleton fun current(repository: RealAuthRepository): CurrentSession = repository
    @Provides fun taskDebugOptions() = TaskDebugOptions()
    @Provides fun authPrefill() = AuthPrefill()
    @Provides fun login(repository: AuthRepository) = LoginUseCase(repository)
    @Provides fun restore(repository: AuthRepository) = RestoreSessionUseCase(repository)
    @Provides fun logout(repository: AuthRepository) = LogoutUseCase(repository)
    @Provides fun environment(repository: AuthRepository) = ChangeEnvironmentUseCase(repository)
    @Provides @Singleton fun database(@ApplicationContext context: Context): CommandDatabase = Room.inMemoryDatabaseBuilder(context, CommandDatabase::class.java).build()
    @Provides @Singleton fun journal(database: CommandDatabase): CommandJournal = RoomCommandJournal(database)
    @Provides @Singleton fun mapper(json: Json) = JournalMapper(json)
    @Provides @Singleton fun sender(factory: ApiFactory, current: CurrentSession): RawCommandSender = OkHttpCommandSender(factory, current)
    @Provides @Singleton fun dispatcher(journal: CommandJournal, sender: RawCommandSender, current: CurrentSession, json: Json, errors: ApiErrorMapper, mapper: JournalMapper) = CommandDispatcher(journal, sender, current, json, errors, mapper)
    @Provides @Singleton fun pending(journal: CommandJournal, mapper: JournalMapper, current: CurrentSession, dispatcher: CommandDispatcher): PendingCommandRepository = RealPendingCommandRepository(journal, mapper, current, dispatcher)
    @Provides fun retry(repository: PendingCommandRepository) = RetryPendingCommandUseCase(repository)
}
