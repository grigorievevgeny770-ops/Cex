package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.local.pending.*
import com.logunovinc.workshopmanager.data.remote.CurrentSession
import com.logunovinc.workshopmanager.domain.pending.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

class RealPendingCommandRepository(
    private val journal: CommandJournal, private val mapper: JournalMapper,
    private val current: CurrentSession, private val dispatcher: CommandDispatcher,
) : PendingCommandRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(): Flow<List<PendingCommand>> = current.profiles.flatMapLatest { profile ->
        if (profile == null) flowOf(emptyList())
        else journal.observe(profile.baseUrl, profile.actorId).map { commands ->
            if (current.matches(profile)) commands.map(mapper::domain) else emptyList()
        }
    }
    override suspend fun retry(commandId: String) = dispatcher.retry(commandId)
}
