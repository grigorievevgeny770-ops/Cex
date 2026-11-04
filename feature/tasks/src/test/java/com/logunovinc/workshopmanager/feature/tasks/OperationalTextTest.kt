package com.logunovinc.workshopmanager.feature.tasks

import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.pending.CommandSummary
import org.junit.Test
import org.junit.Assert.*

class OperationalTextTest {
    @Test fun journalProjectionHidesOnlyGeneratedIdsAndVersionLabelsWithoutChangingSavedEvidence() {
        val summary=CommandSummary("Приёмка · задание №101",listOf("Материал · строка №1001: 4.0000 шт","Версия задания 3, перемещения 2","Листы: 9 · длина 1.234 м","Версия задания новая: 2 шт","Причина: партия №123 не прибыла"),"Указание №77")
        assertEquals("Приёмка",summary.operationalTitle())
        assertEquals(listOf("Материал: 4.0000 шт","Листы: 9 · длина 1.234 м","Версия задания новая: 2 шт","Причина: партия №123 не прибыла"),summary.operationalLines())
        assertEquals("Указание №77",summary.comment)
        assertTrue(summary.lines[0].contains("1001"));assertTrue(summary.title.contains("101"))
    }
    @Test fun zeroBaseSheetEvidenceAndUnknownDimensionRemainVisible() {
        val summary=CommandSummary("Первый выпуск",listOf("Изделие: 9 листов","Материал: 0.0000 м²","Длина 0.001 м","Свободных листов: неизвестно"))
        assertEquals(summary.lines,summary.operationalLines())
    }
    @Test fun knownConflictHasShortRussianHintWhileSpecificShortagesKeepTheirMeaning() {
        assertFalse(AppError(ErrorKind.CONFLICT,"Task version or state changed","DOCUMENT_STATE_CONFLICT").operationalMessage().contains("Task"))
        assertEquals("Не хватает 6 шт",AppError(ErrorKind.CONFLICT,"Не хватает 6 шт","INSUFFICIENT_STOCK").operationalMessage())
    }
}
