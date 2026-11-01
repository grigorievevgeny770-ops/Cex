package com.logunovinc.workshopmanager.domain.pending

import com.logunovinc.workshopmanager.domain.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RetryPendingCommandUseCaseTest {
    @Test fun invalidIdDoesNotReachRepository() = runTest {
        var called = false
        val repository = object : PendingCommandRepository {
            override fun observe() = flowOf(emptyList<PendingCommand>())
            override suspend fun retry(commandId: String): CommandOutcome { called = true; return CommandOutcome.Rejected(null, AppError(ErrorKind.NETWORK, "unknown")) }
        }
        assertTrue(RetryPendingCommandUseCase(repository)("") is CommandOutcome.Rejected)
        assertFalse(called)
        RetryPendingCommandUseCase(repository)("saved-id")
        assertTrue(called)
    }
}
