package com.logunovinc.workshopmanager.feature.tasks

import com.logunovinc.workshopmanager.domain.pending.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryCompletionTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    @Test fun archivedAckDoesNotTriggerNavigationButCurrentExplicitRecoveryPublishesDurableReceipt() = runTest(dispatcher) {
        val ack=CommandConfirmation("pending",101,9,null,true,terminal=true)
        val stored=PendingCommand("archived",101,"CLOSE_INCOMPLETE",CommandSummary("Закрытие",emptyList()),PendingState.ACKNOWLEDGED,Instant.EPOCH,1,confirmation=ack.copy(commandId="archived"))
        var retries=0
        val repository=object:PendingCommandRepository {
            override fun observe()=MutableStateFlow(listOf(stored,stored.copy(id="pending",state=PendingState.UNKNOWN,confirmation=null)))
            override suspend fun retry(commandId:String):CommandOutcome { retries++;return CommandOutcome.Confirmed(ack) }
        }
        val vm=RecoveryViewModel(repository,RetryPendingCommandUseCase(repository));runCurrent()
        assertNull(vm.uiState.value.lastConfirmation)
        vm.onIntent(RecoveryIntent.Retry("pending"));runCurrent()
        assertEquals(ack,vm.uiState.value.lastConfirmation);assertEquals(1,retries)
    }
}
