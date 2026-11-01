@file:kotlinx.serialization.UseSerializers(DecimalSerializer::class)
package com.logunovinc.workshopmanager.data.remote.dto

import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable data class RecipeOutputDto(val qty: BigDecimal, val sheets: Int? = null, @SerialName("base_unit") val baseUnit: String, @SerialName("sheet_length_m") val sheetLength: BigDecimal? = null)
@Serializable data class RecipeMaterialDto(@SerialName("product_id") val productId: Long, @SerialName("product_name") val productName: String,
    val sku: String? = null, val qty: BigDecimal, val sheets: Int? = null, @SerialName("sheet_length_m") val sheetLength: BigDecimal? = null,
    @SerialName("base_unit") val baseUnit: String, @SerialName("unit_name") val unitName: String? = null, @SerialName("unit_qty") val unitQuantity: BigDecimal? = null)
@Serializable data class ProductionDescriptorDto(@SerialName("schema_version") val schemaVersion: Int = 0,
    val mode: String = "unknown", @SerialName("recipe_status") val status: String? = null, @SerialName("recipe_version") val recipeVersion: Long = 0,
    @SerialName("recipe_id") val recipeId: Long? = null, val source: String? = null,
    @SerialName("learned_from_execution_id") val learnedFromExecutionId: Long? = null,
    @SerialName("created_by") val createdBy: Long? = null, @SerialName("created_at") val createdAt: String? = null,
    @SerialName("basis_measure") val basisMeasure: String? = null, @SerialName("basis_output") val basisOutput: RecipeOutputDto? = null,
    val materials: List<RecipeMaterialDto> = emptyList())
@Serializable data class ManufacturingOutputDto(@SerialName("task_line_id") val taskLineId: Long, val qty: BigDecimal? = null, val sheets: Int? = null)
@Serializable data class RawMaterialDto(@SerialName("product_id") val productId: Long, val qty: BigDecimal? = null, val sheets: Int? = null,
    @SerialName("sheet_length_m") val sheetLength: BigDecimal? = null, @SerialName("unit_name") val unitName: String? = null, @SerialName("unit_qty") val unitQuantity: BigDecimal? = null)
@Serializable data class ByRecipePreviewDto(val mode: String = "by_recipe", @SerialName("expected_version") val taskVersion: Long,
    @SerialName("expected_recipe_version") val recipeVersion: Long, val output: ManufacturingOutputDto)
@Serializable data class PrototypePreviewDto(val mode: String = "prototype", @SerialName("expected_version") val taskVersion: Long,
    @SerialName("expected_recipe_version") val recipeVersion: Long, val output: ManufacturingOutputDto, val materials: List<RawMaterialDto>)
@Serializable data class ByRecipeExecutionDto(val mode: String = "by_recipe", @SerialName("expected_version") val taskVersion: Long,
    @SerialName("expected_recipe_version") val recipeVersion: Long, @SerialName("client_token") val clientToken: String,
    val output: ManufacturingOutputDto, val comment: String = "")
@Serializable data class PrototypeExecutionDto(val mode: String = "prototype", @SerialName("expected_version") val taskVersion: Long,
    @SerialName("expected_recipe_version") val recipeVersion: Long, @SerialName("client_token") val clientToken: String,
    val output: ManufacturingOutputDto, val materials: List<RawMaterialDto>, val comment: String = "")
@Serializable data class NormalizedOutputDto(@SerialName("task_line_id") val taskLineId: Long, @SerialName("product_id") val productId: Long,
    @SerialName("product_name") val productName: String, @SerialName("base_unit") val baseUnit: String, val qty: BigDecimal, val sheets: Int? = null,
    @SerialName("sheet_length_m") val sheetLength: BigDecimal? = null, @SerialName("unit_name") val unitName: String? = null, @SerialName("unit_qty") val unitQuantity: BigDecimal? = null)
@Serializable data class PreviewMaterialDto(@SerialName("product_id") val productId: Long, @SerialName("product_name") val productName: String,
    val sku: String? = null, @SerialName("base_unit") val baseUnit: String, val qty: BigDecimal, val sheets: Int? = null,
    @SerialName("sheet_length_m") val sheetLength: BigDecimal? = null, @SerialName("unit_name") val unitName: String? = null,
    @SerialName("unit_qty") val unitQuantity: BigDecimal? = null, @SerialName("stock_qty") val stockQuantity: BigDecimal,
    @SerialName("reserved_qty") val reservedQuantity: BigDecimal, @SerialName("free_qty") val freeQuantity: BigDecimal,
    @SerialName("stock_sheets") val stockSheets: Int? = null, @SerialName("reserved_sheets") val reservedSheets: Int? = null,
    @SerialName("free_sheets") val freeSheets: Int? = null, @SerialName("missing_qty") val missingQuantity: BigDecimal,
    @SerialName("missing_sheets") val missingSheets: Int? = null, @SerialName("dimension_availability") val dimensionAvailability: String)
@Serializable data class PreviewIssueDto(val code: String, val message: String, @SerialName("product_id") val productId: Long? = null,
    @SerialName("task_line_id") val taskLineId: Long? = null, @SerialName("material_index") val materialIndex: Int? = null, val details: JsonObject? = null)
@Serializable data class ManufacturingPreviewDto(@SerialName("task_id") val taskId: Long, @SerialName("task_version") val taskVersion: Long,
    @SerialName("recipe_version") val recipeVersion: Long, val mode: String, val output: NormalizedOutputDto,
    val materials: List<PreviewMaterialDto>, @SerialName("can_execute") val canExecute: Boolean, val issues: List<PreviewIssueDto>,
    @SerialName("proposed_recipe") val proposedRecipe: ProductionDescriptorDto? = null)
