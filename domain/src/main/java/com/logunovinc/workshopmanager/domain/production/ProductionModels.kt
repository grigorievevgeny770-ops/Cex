package com.logunovinc.workshopmanager.domain.production

import java.math.BigDecimal
import java.time.Instant
import com.logunovinc.workshopmanager.domain.task.ActualLine
import com.logunovinc.workshopmanager.domain.task.ActualLineInput

enum class ProductionMode { FIXED_RECIPE, PROTOTYPE_REQUIRED, LEGACY_MANUAL_BUDGET, UNKNOWN }
enum class RecipeStatus { READY, AWAITING_PROTOTYPE, UNKNOWN }
enum class BasisMeasure { QTY, SHEETS, UNKNOWN }
enum class ManufacturingMode { BY_RECIPE, PROTOTYPE, UNKNOWN }
enum class DimensionAvailability { KNOWN, UNKNOWN, NOT_APPLICABLE, UNSUPPORTED }
data class RecipeOutput(val quantity: BigDecimal, val sheets: Int?, val baseUnit: String, val sheetLength: BigDecimal?)
data class RecipeMaterial(val productId: Long, val productName: String, val sku: String?, val quantity: BigDecimal, val sheets: Int?,
    val sheetLength: BigDecimal?, val baseUnit: String, val unitName: String?, val unitQuantity: BigDecimal?)
data class ProductionDescriptor(val schemaVersion: Int, val mode: ProductionMode, val status: RecipeStatus, val recipeVersion: Long,
    val recipeId: Long?, val source: String?, val learnedFromExecutionId: Long?, val createdBy: Long?, val createdAt: Instant?,
    val basisMeasure: BasisMeasure?, val basisOutput: RecipeOutput?, val materials: List<RecipeMaterial>) {
    val supported: Boolean get() = schemaVersion == 2 && when (mode) {
        ProductionMode.FIXED_RECIPE -> ready
        ProductionMode.PROTOTYPE_REQUIRED -> (status == RecipeStatus.AWAITING_PROTOTYPE && recipeVersion == 0L && basisOutput == null && materials.isEmpty()) || ready
        else -> false
    }
    private val ready: Boolean get() = status == RecipeStatus.READY && recipeVersion >= 1 && materials.isNotEmpty() && when (basisMeasure) {
        BasisMeasure.QTY -> basisOutput?.quantity?.signum() == 1
        BasisMeasure.SHEETS -> (basisOutput?.sheets ?: 0) > 0
        else -> false
    }
}
data class PrototypeMaterialInput(val productId: Long, val quantity: String = "", val sheets: String = "", val sheetLength: String = "", val unitName: String = "", val unitQuantity: String = "")
data class PrototypeMaterial(val productId: Long, val quantity: BigDecimal?, val sheets: Int?, val sheetLength: BigDecimal?, val unitName: String?, val unitQuantity: BigDecimal?)
data class ManufacturingDraft(val output: ActualLineInput, val materials: List<PrototypeMaterialInput> = emptyList())
sealed interface ManufacturingCommand {
    val taskVersion: Long
    val recipeVersion: Long
    val output: ActualLine
    val mode: ManufacturingMode
    data class ByRecipe(override val taskVersion: Long, override val recipeVersion: Long, override val output: ActualLine) : ManufacturingCommand { override val mode = ManufacturingMode.BY_RECIPE }
    data class Prototype(override val taskVersion: Long, override val recipeVersion: Long, override val output: ActualLine, val materials: List<PrototypeMaterial>) : ManufacturingCommand { override val mode = ManufacturingMode.PROTOTYPE }
}
data class NormalizedOutput(val taskLineId: Long, val productId: Long, val productName: String, val baseUnit: String,
    val quantity: BigDecimal, val sheets: Int?, val sheetLength: BigDecimal?, val unitName: String?, val unitQuantity: BigDecimal?)
data class PreviewMaterial(val productId: Long, val productName: String, val sku: String?, val baseUnit: String,
    val quantity: BigDecimal, val sheets: Int?, val sheetLength: BigDecimal?, val unitName: String?, val unitQuantity: BigDecimal?,
    val stockQuantity: BigDecimal, val reservedQuantity: BigDecimal, val freeQuantity: BigDecimal,
    val stockSheets: Int?, val reservedSheets: Int?, val freeSheets: Int?, val missingQuantity: BigDecimal, val missingSheets: Int?, val dimensionAvailability: DimensionAvailability)
data class PreviewIssue(val code: String, val message: String, val productId: Long?, val taskLineId: Long?, val materialIndex: Int?, val details: List<Pair<String, String>>)
data class ManufacturingPreview(val taskId: Long, val taskVersion: Long, val recipeVersion: Long, val mode: ManufacturingMode,
    val output: NormalizedOutput, val materials: List<PreviewMaterial>, val canExecute: Boolean, val issues: List<PreviewIssue>, val proposedRecipe: ProductionDescriptor?)
