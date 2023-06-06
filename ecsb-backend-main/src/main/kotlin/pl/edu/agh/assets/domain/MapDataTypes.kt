package pl.edu.agh.assets.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.utils.Utils
import pl.edu.agh.utils.getLogger

sealed class MapDataTypes(val dataName: String, val dataValue: String) {

    @Serializable(with = TripSerializer::class)
    sealed class Trip(dataValue: String) : MapDataTypes("trip", dataValue) {
        object Low : Trip("low")
        object Medium : Trip("medium")
        object High : Trip("high")

        companion object {
            val All: List<MapDataTypes.Trip> = listOf(Low, Medium, High)

            fun fromString(name: String): Trip = when (name) {
                "low" -> Trip.Low
                "medium" -> Trip.Medium
                "high" -> Trip.High
                else -> throw exception("trip", name)
            }
        }
    }

    object TripSerializer : KSerializer<Trip> {
        override val descriptor: SerialDescriptor = String.serializer().descriptor

        override fun deserialize(decoder: Decoder): Trip =
            Trip.fromString(decoder.decodeString())

        override fun serialize(encoder: Encoder, value: Trip) {
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
                    "trip" -> Trip.fromString(dataValue)
                    "workshop" -> Workshop(GameClassName(dataValue))
                    "startingPoint" -> StartingPoint
                    else -> throw exception(dataName, dataValue)
                }
            }
    }
}
