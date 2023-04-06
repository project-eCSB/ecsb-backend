package pl.edu.agh.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DateSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)

    private val dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME

    override fun deserialize(decoder: Decoder): LocalDateTime {
        return LocalDateTime.parse(decoder.decodeString(), dateTimeFormatter)
    }

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.format(dateTimeFormatter))
    }
}
