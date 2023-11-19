package pl.edu.agh.moving.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = Direction.Serializer::class)
enum class Direction(val value: String) {
    NONE("none"),
    LEFT("left"),
    UP_LEFT("up-left"),
    UP("up"),
    UP_RIGHT("up-right"),
    RIGHT("right"),
    DOWN_RIGHT("down-right"),
    DOWN("down"),
    DOWN_LEFT("down-left");

    internal object Serializer : KSerializer<Direction> {
        override val descriptor: SerialDescriptor = String.serializer().descriptor

        override fun deserialize(decoder: Decoder): Direction {
            val value = decoder.decodeString()
            return values().find { it.value == value } ?: NONE
        }

        override fun serialize(encoder: Encoder, value: Direction) {
            encoder.encodeString(value.value)
        }
    }
}
