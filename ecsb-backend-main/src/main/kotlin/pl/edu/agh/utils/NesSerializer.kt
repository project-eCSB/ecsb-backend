package pl.edu.agh.utils

import arrow.core.NonEmptySet
import arrow.core.toNonEmptySetOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

typealias NonEmptySetS<T> =
    @Serializable(with = NesSerializer::class)
    NonEmptySet<T>

class NesSerializer<T>(val serializer: KSerializer<T>) : KSerializer<NonEmptySet<T>> {

    override fun deserialize(decoder: Decoder): NonEmptySet<T> =
        SetSerializer(serializer).deserialize(decoder).toNonEmptySetOrNull()!!

    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun serialize(encoder: Encoder, value: NonEmptySet<T>): Unit =
        SetSerializer(serializer).serialize(encoder, value.toSet())
}
