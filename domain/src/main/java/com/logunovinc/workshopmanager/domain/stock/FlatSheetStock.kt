package com.logunovinc.workshopmanager.domain.stock

/**
 * Flat sheet is the only raw material the workshop currently processes.
 * [Product.sheet] is the only catalog flag in the contract. Category ids are not stored here.
 */
fun Product.isFlatSheet(): Boolean = sheet

data class FlatSheetClassification(val sheets: List<StockPosition>, val hidden: Int, val unverified: Int)

/** Keeps positions whose catalog product is flat sheet. Other goods are omitted, not relabeled as finished output. */
fun classifyFlatSheet(positions: List<StockPosition>, products: Map<Long, Product?>): FlatSheetClassification {
    val sheets = mutableListOf<StockPosition>()
    var hidden = 0
    var unverified = 0
    for (position in positions) {
        when (val product = products[position.productId]) {
            null -> unverified++
            else -> if (product.id == position.productId && product.isFlatSheet()) sheets += position else hidden++
        }
    }
    return FlatSheetClassification(sheets, hidden, unverified)
}
