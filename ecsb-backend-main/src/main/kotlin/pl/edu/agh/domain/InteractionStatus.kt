package pl.edu.agh.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = InteractionStatus.Serializer::class)
enum class InteractionStatus(val value: String) {
    BUSY("busy"),
    NOT_BUSY("not_busy");

    internal object Serializer : KSerializer<InteractionStatus> {
        override val descriptor: SerialDescriptor = String.serializer().descriptor

        override fun deserialize(decoder: Decoder): InteractionStatus {
            val value = decoder.decodeString()
            return InteractionStatus.values().find { it.value == value } ?: NOT_BUSY
        }

        override fun serialize(encoder: Encoder, value: InteractionStatus) {
            encoder.encodeString(value.value)
        }
    }
}
