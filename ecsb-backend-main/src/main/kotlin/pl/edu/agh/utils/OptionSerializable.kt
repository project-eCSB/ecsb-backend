package pl.edu.agh.utils

import arrow.core.Option
import arrow.core.toOption
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

class OptSerializer<T>(val serializer: KSerializer<T?>) : KSerializer<Option<T>> {

    override fun deserialize(decoder: Decoder): Option<T> = serializer.deserialize(decoder).toOption()

    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun serialize(encoder: Encoder, value: Option<T>) {
        serializer.serialize(encoder, value.getOrNull())
    }
}

object InstantSerializer : KSerializer<Instant> {

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())

    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }
}
