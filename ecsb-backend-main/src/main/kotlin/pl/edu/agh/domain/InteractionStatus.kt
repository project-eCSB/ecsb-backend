package pl.edu.agh.domain

import arrow.core.getOrElse
import arrow.core.toOption
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.exposed.sql.VarCharColumnType
import pl.edu.agh.utils.BaseDBWrapper
import pl.edu.agh.utils.NonNegInt.Companion.LiteralOpOwn

@Serializable(with = InteractionStatus.Serializer::class)
enum class InteractionStatus(val value: String) {
    COOP_BUSY("coop"),
    TRAVEL_BUSY("travel"),
    TRADE_BUSY("trade"),
    PRODUCTION_BUSY("production"),
    NOT_BUSY("not busy");

    fun literal(): LiteralOpOwn<InteractionStatus> =
        LiteralOpOwn(BaseDBWrapper(VarCharColumnType(), InteractionStatus::toDB, InteractionStatus::fromDB), this)

    internal object Serializer : KSerializer<InteractionStatus> {
        override val descriptor: SerialDescriptor = String.serializer().descriptor

        override fun deserialize(decoder: Decoder): InteractionStatus {
            val value = decoder.decodeString()
            return InteractionStatus.enumMap[value] ?: NOT_BUSY
        }

        override fun serialize(encoder: Encoder, value: InteractionStatus) {
            encoder.encodeString(value.value)
        }
    }

    companion object {
        fun toDB(value: InteractionStatus): String =
            value.value

        private val enumMap: Map<String, InteractionStatus> = values().toList().associateBy { it.value }

        @Throws(IllegalStateException::class)
        fun fromDB(str: String?): InteractionStatus =
            str.toOption().map {
                enumMap[it] ?: error("Couldn't find value for $it")
            }.getOrElse { NOT_BUSY }
    }
}
