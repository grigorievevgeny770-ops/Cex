package com.logunovinc.workshopmanager.data.remote.dto

import java.math.BigDecimal
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Wire decimals are strings; scale is preserved and never formatted for display. */
object DecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("Decimal", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
    override fun deserialize(decoder: Decoder): BigDecimal = decoder.decodeString().toBigDecimal()
}
