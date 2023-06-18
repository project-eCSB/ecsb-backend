package pl.edu.agh.utils

import arrow.core.NonEmptyList
import arrow.core.toNonEmptyListOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

typealias NonEmptyListS<T> =
    @Serializable(with = NelSerializer::class)
    NonEmptyList<T>

class NelSerializer<T>(val serializer: KSerializer<T>) : KSerializer<NonEmptyList<T>> {

    override fun deserialize(decoder: Decoder): NonEmptyList<T> =
        ListSerializer(serializer).deserialize(decoder).toNonEmptyListOrNull()!!

    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun serialize(encoder: Encoder, value: NonEmptyList<T>): Unit =
        ListSerializer(serializer).serialize(encoder, value.toList())
}
