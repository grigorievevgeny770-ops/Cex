package com.logunovinc.workshopmanager.domain.task

import com.logunovinc.workshopmanager.domain.*
import org.junit.Test
import org.junit.Assert.*

class CloseIncompleteTest {
    @Test fun reasonUsesTrimmedUnicodeCodePointsWithoutTruncation() {
        for(raw in listOf(""," \n\t ","a".repeat(2001),"😀".repeat(2001))) assertTrue(CloseIncompleteReason.validate(raw) is Outcome.Failure)
        assertEquals("Причина",(CloseIncompleteReason.validate("  Причина \n") as Outcome.Success).value)
        assertEquals("😀".repeat(2000),(CloseIncompleteReason.validate("😀".repeat(2000)) as Outcome.Success).value)
    }
    @Test fun closeTypeAndActionGateDoesNotDependOnManufacturingRecipeReadiness() {
        for(type in TaskType.entries) {
            val task=sampleTask(type=type,actions=setOf("execute","close_incomplete"))
            assertEquals(type==TaskType.DISPATCH_GOODS || type==TaskType.MANUFACTURE_PRODUCT,task.canCloseIncomplete)
            assertFalse(task.copy(isClosedIncomplete=true).canExecute)
            assertFalse(task.copy(isClosedIncomplete=true).canCloseIncomplete)
            assertFalse(task.copy(allowedActions=setOf("execute")).canCloseIncomplete)
            assertFalse(task.copy(isCompleted=true).canCloseIncomplete)
            assertFalse(task.copy(isCancelled=true).canCloseIncomplete)
            assertFalse(task.copy(isCompleted=null).canCloseIncomplete)
        }
    }
}
