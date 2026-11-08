package com.logunovinc.workshopmanager.domain.order

import java.math.BigDecimal

data class ProfileSegment(val startMm: Long, val endMm: Long?)
data class ProfileEdge(val type: String, val direction: String)
data class ProfileDrawing(
    val requestId: Long, val lineId: Long, val version: Long,
    val skewed: Boolean, val lengthMm: Long, val blankWidthMm: Long,
    val segments: List<ProfileSegment>, val angles: List<BigDecimal>, val turns: List<String>,
    val edge1: ProfileEdge, val edge2: ProfileEdge,
)
