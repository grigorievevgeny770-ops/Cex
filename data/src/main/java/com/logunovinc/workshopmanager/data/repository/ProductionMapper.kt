package com.logunovinc.workshopmanager.data.repository

import com.logunovinc.workshopmanager.data.remote.dto.*
import com.logunovinc.workshopmanager.domain.production.*
import com.logunovinc.workshopmanager.domain.task.ActualLine
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class ProductionMapper {
    fun descriptor(value: ProductionDescriptorDto) = ProductionDescriptor(value.schemaVersion,
        ProductionMode.entries.find { it.name.lowercase() == value.mode } ?: ProductionMode.UNKNOWN,
        RecipeStatus.entries.find { it.name.lowercase() == value.status } ?: RecipeStatus.UNKNOWN, value.recipeVersion, value.recipeId, value.source,
        value.learnedFromExecutionId, value.createdBy, value.createdAt?.let { OffsetDateTime.parse(it).toInstant() },
        value.basisMeasure?.let { raw -> BasisMeasure.entries.find { it.name.lowercase() == raw } ?: BasisMeasure.UNKNOWN },
        value.basisOutput?.let { RecipeOutput(it.qty, it.sheets, it.baseUnit, it.sheetLength) },
        value.materials.map { RecipeMaterial(it.productId, it.productName, it.sku, it.qty, it.sheets, it.sheetLength, it.baseUnit, it.unitName, it.unitQuantity) })
    fun output(value: ActualLine) = ManufacturingOutputDto(value.taskLineId, value.quantity, value.sheets)
    fun materials(value: ManufacturingCommand.Prototype) = value.materials.map { RawMaterialDto(it.productId, it.quantity, it.sheets, it.sheetLength, it.unitName, it.unitQuantity) }
    fun preview(value: ManufacturingPreviewDto) = ManufacturingPreview(value.taskId, value.taskVersion, value.recipeVersion,
        ManufacturingMode.entries.find { it.name.lowercase() == value.mode } ?: ManufacturingMode.UNKNOWN,
        value.output.let { NormalizedOutput(it.taskLineId, it.productId, it.productName, it.baseUnit, it.qty, it.sheets, it.sheetLength, it.unitName, it.unitQuantity) },
        value.materials.map { PreviewMaterial(it.productId, it.productName, it.sku, it.baseUnit, it.qty, it.sheets, it.sheetLength, it.unitName, it.unitQuantity,
            it.stockQuantity, it.reservedQuantity, it.freeQuantity, it.stockSheets, it.reservedSheets, it.freeSheets, it.missingQuantity, it.missingSheets,
            DimensionAvailability.entries.find { entry -> entry.name.lowercase() == it.dimensionAvailability } ?: DimensionAvailability.UNSUPPORTED) },
        value.canExecute, value.issues.map { issue -> PreviewIssue(issue.code, issue.message, issue.productId, issue.taskLineId, issue.materialIndex,
            issue.details.orEmpty().filterKeys { it in setOf("required_qty", "available_qty", "missing_qty", "requested_sheets", "available_sheets", "next_supported_output_qty") }
                .mapNotNull { (key, content) -> (content as? JsonPrimitive)?.contentOrNull?.let { key to it } }) }, value.proposedRecipe?.let(::descriptor))
}
