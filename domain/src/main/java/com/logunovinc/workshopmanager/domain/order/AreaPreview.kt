package com.logunovinc.workshopmanager.domain.order

import java.math.BigDecimal

data class AreaPreviewLine(val lineId:Long,val drawingVersion:Long,val productName:String,
    val blankWidthMm:Long,val lengthMm:Long,val pieceQuantity:BigDecimal,val areaM2:BigDecimal)
data class AreaPreviewMaterial(val productId:Long,val productName:String,val areaM2:BigDecimal)
data class AreaPreview(val requestId:Long,val requestVersion:Long,val hash:String,val totalAreaM2:BigDecimal,
    val lines:List<AreaPreviewLine>,val materials:List<AreaPreviewMaterial>) {
    fun matches(order:WorkshopOrder):Boolean = requestId==order.id && requestVersion==order.version &&
        hash.matches(Regex("[a-f0-9]{64}")) && lines.size==order.lines.size &&
        lines.map { it.lineId }.toSet().size==lines.size && order.lines.all { line ->
            line.drawingVersion!=null && lines.any { it.lineId==line.id && it.drawingVersion==line.drawingVersion }
        }
}
