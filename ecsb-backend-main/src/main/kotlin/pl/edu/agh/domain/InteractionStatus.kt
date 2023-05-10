package pl.edu.agh.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = InteractionStatus.Serializer::class)
enum class InteractionStatus(val value: String) {
    TRADE_IN_PROGRESS("trade_in_progress"),
    TRADE_OFFER("trade_offer"),
    PRODUCTION("production"),
    TRAVEL("travel"),
    COMPANY_IN_PROGRESS("company_in_progress"),
    COMPANY_OFFER("company_offer");

    internal object Serializer : KSerializer<InteractionStatus> {
        override val descriptor: SerialDescriptor = String.serializer().descriptor

        override fun deserialize(decoder: Decoder): InteractionStatus {
            val value = decoder.decodeString()
            return InteractionStatus.values().find { it.value == value } ?: throw Exception("Status not found")
        }

        override fun serialize(encoder: Encoder, value: InteractionStatus) {
            encoder.encodeString(value.value)
        }
    }
}
