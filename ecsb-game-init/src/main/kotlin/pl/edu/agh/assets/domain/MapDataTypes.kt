package pl.edu.agh.assets.domain

import pl.edu.agh.domain.GameClassName
import pl.edu.agh.utils.Utils
import pl.edu.agh.utils.getLogger

sealed class MapDataTypes(val dataName: String, val dataValue: String) {
    sealed class Trip(dataValue: String) : MapDataTypes("trip", dataValue) {
        object Low : Trip("low")
        object Medium : Trip("medium")
        object High : Trip("high")
    }

    class Workshop(className: GameClassName) : MapDataTypes("workshop", className.value)

    object StartingPoint : MapDataTypes("startingPoint", "")

    companion object {
        private fun exception(dataName: String, dataValue: String): NoWhenBranchMatchedException =
            NoWhenBranchMatchedException("Can't match ($dataName, $dataValue)")

        fun fromDB(dataName: String, dataValue: String): MapDataTypes =
            Utils.catchPrint(getLogger(MapDataTypes::class.java)) {
                when (dataName) {
                    "trip" -> when (dataValue) {
                        "low" -> Trip.Low
                        "medium" -> Trip.Medium
                        "high" -> Trip.High
                        else -> throw exception(dataName, dataValue)
                    }

                    "workshop" -> Workshop(GameClassName(dataValue))
                    "startingPoint" -> StartingPoint
                    else -> throw exception(dataName, dataValue)
                }
            }
    }
}
