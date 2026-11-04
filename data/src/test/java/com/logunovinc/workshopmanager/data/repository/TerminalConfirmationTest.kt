package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.local.pending.*
import com.logunovinc.workshopmanager.domain.pending.*
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*

class TerminalConfirmationTest {
    private val mapper=JournalMapper(Json { ignoreUnknownKeys=true })
    private fun receipt(task:String,result:String="") = mapper.confirmation(PendingCommandEntity("saved","https://example.com/",42,101,"execute","token","POST","api/workshop/tasks/101/executions",byteArrayOf(),"fingerprint","{}",0,PendingState.ACKNOWLEDGED.name,
        ackBody="""{"result":{"id":101,"duplicate":true$result},"task":{"id":101,"version":9$task}}"""))!!
    @Test fun terminalComesOnlyFromCurrentAckTaskNotOldDuplicateResultOrQuantities() {
        assertTrue(receipt(",\"is_completed\":true,\"is_closed_incomplete\":false").terminal)
        assertTrue(receipt(",\"is_completed\":false,\"is_closed_incomplete\":true").terminal)
        assertFalse(receipt(",\"is_completed\":false,\"is_cancelled\":false",",\"is_completed\":true,\"is_closed_incomplete\":true").terminal)
        assertFalse(receipt(",\"remaining_qty\":\"0\"").terminal)
        assertFalse(receipt(",\"is_cancelled\":true").terminal)
        assertEquals(9L,receipt(",\"is_completed\":true",",\"version\":2").taskVersion)
    }
    @Test fun legacyCompletionFallbackRequiresBothNewFlagsAbsent() {
        assertTrue(receipt(",\"status\":\"completed\"").terminal)
        assertFalse(receipt(",\"is_completed\":false,\"status\":\"completed\"").terminal)
        assertFalse(receipt(",\"is_cancelled\":false,\"status\":\"completed\"").terminal)
        assertFalse(receipt(",\"status\":\"future\"").terminal)
    }
}
