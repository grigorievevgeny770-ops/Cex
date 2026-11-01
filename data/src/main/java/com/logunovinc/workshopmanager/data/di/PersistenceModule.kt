package com.logunovinc.workshopmanager.data.di

import android.content.Context
import androidx.room.Room
import com.logunovinc.workshopmanager.data.local.pending.*
import com.logunovinc.workshopmanager.data.remote.*
import com.logunovinc.workshopmanager.data.repository.*
import com.logunovinc.workshopmanager.domain.pending.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object PersistenceModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): CommandDatabase =
        Room.databaseBuilder(context, CommandDatabase::class.java, "pending-commands.db").build()
    @Provides @Singleton fun journal(database: CommandDatabase): CommandJournal = RoomCommandJournal(database)
    @Provides @Singleton fun journalMapper(json: Json) = JournalMapper(json)
    @Provides @Singleton fun sender(factory: ApiFactory, current: CurrentSession): RawCommandSender = OkHttpCommandSender(factory, current)
    @Provides @Singleton fun dispatcher(journal: CommandJournal, sender: RawCommandSender, current: CurrentSession, json: Json, errors: ApiErrorMapper, mapper: JournalMapper) =
        CommandDispatcher(journal, sender, current, json, errors, mapper)
    @Provides @Singleton fun pending(journal: CommandJournal, mapper: JournalMapper, current: CurrentSession, dispatcher: CommandDispatcher): PendingCommandRepository =
        RealPendingCommandRepository(journal, mapper, current, dispatcher)
    @Provides fun retry(repository: PendingCommandRepository) = RetryPendingCommandUseCase(repository)
}
