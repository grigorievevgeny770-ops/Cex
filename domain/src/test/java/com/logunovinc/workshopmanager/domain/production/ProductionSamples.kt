package com.logunovinc.workshopmanager.domain.production
import java.math.BigDecimal
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.production.*
fun productionTask(prototype: Boolean = false, ready: Boolean = !prototype, version: Long = if (prototype && !ready) 2 else 3): Task {
    val descriptor = ProductionDescriptor(2, if (prototype) ProductionMode.PROTOTYPE_REQUIRED else ProductionMode.FIXED_RECIPE,
        if (ready) RecipeStatus.READY else RecipeStatus.AWAITING_PROTOTYPE, if (ready) 1 else 0, if (ready) 701 else null,
        if (ready) "admin" else null, null, null, null, if (ready) BasisMeasure.QTY else null,
        if (ready) RecipeOutput(BigDecimal("10"), null, "шт", null) else null,
        if (ready) listOf(RecipeMaterial(301, "Материал", null, BigDecimal("20"), null, null, "шт", null, null)) else emptyList())
    return sampleTask(type = TaskType.MANUFACTURE_PRODUCT, version = version).copy(production = descriptor, lines = listOf(
        TaskLine(3002, 501, "Изделие", null, "output", "шт", null, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, null, null, null, null, null, null, null)))
}
fun manufacturingPreview(task: Task, command: ManufacturingCommand, canExecute: Boolean = true): ManufacturingPreview = ManufacturingPreview(task.id, command.taskVersion, command.recipeVersion, command.mode,
    NormalizedOutput(3002, 501, "Изделие", "шт", command.output.quantity ?: BigDecimal.ZERO, command.output.sheets, null, null, null),
    listOf(PreviewMaterial(301, "Материал", null, "шт", BigDecimal("6"), null, null, null, null, BigDecimal("100"), BigDecimal.TEN, BigDecimal("90"), null, null, null, BigDecimal.ZERO, null, DimensionAvailability.NOT_APPLICABLE)), canExecute, emptyList(),
    if (command is ManufacturingCommand.Prototype) productionTask(prototype = true, ready = true).production!!.copy(
        basisOutput = RecipeOutput(BigDecimal("2"), null, "шт", null), materials = listOf(RecipeMaterial(301, "Материал", null, BigDecimal("4"), null, null, "шт", null, null))) else null)
