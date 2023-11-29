package pl.edu.agh.assets.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.utils.Utils
import pl.edu.agh.utils.getLogger

sealed class MapDataTypes(val dataName: String, val dataValue: String) {

    override fun hashCode(): Int {
        return (dataName to dataValue).hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return if (other is MapDataTypes) {
            (dataName to dataValue) == (other.dataName to other.dataValue)
        } else {
            super.equals(other)
        }
    }

    @Serializable(with = TravelSerializer::class)
    sealed class Travel(dataValue: String) : MapDataTypes("travel", dataValue) {
        object Low : Travel("low")
        object Medium : Travel("medium")
        object High : Travel("high")

        companion object {
            val All: List<Travel> = listOf(Low, Medium, High)

            fun fromString(name: String): Travel = when (name) {
                "low" -> Travel.Low
                "medium" -> Travel.Medium
                "high" -> Travel.High
                else -> throw exception("travel", name)
            }
        }
    }

    object TravelSerializer : KSerializer<Travel> {
        override val descriptor: SerialDescriptor = String.serializer().descriptor

        override fun deserialize(decoder: Decoder): Travel =
            Travel.fromString(decoder.decodeString())

        override fun serialize(encoder: Encoder, value: Travel) {
            encoder.encodeString(value.dataValue)
        }
    }

    class Workshop(className: GameClassName) : MapDataTypes("workshop", className.value)

    object StartingPoint : MapDataTypes("startingPoint", "")

    companion object {
        private fun exception(dataName: String, dataValue: String): NoWhenBranchMatchedException =
            NoWhenBranchMatchedException("Can't match ($dataName, $dataValue)")

        fun fromDB(dataName: String, dataValue: String): MapDataTypes =
            Utils.catchPrint(getLogger(MapDataTypes::class.java)) {
                when (dataName) {
                    "travel" -> Travel.fromString(dataValue)
                    "workshop" -> Workshop(GameClassName(dataValue))
                    "startingPoint" -> StartingPoint
                    else -> throw exception(dataName, dataValue)
                }
            }
    }
}
