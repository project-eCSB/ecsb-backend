package pl.edu.agh.assets.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.utils.NonEmptyListSer

@Serializable
data class MapAssetDataDto(
    val lowLevelTrips: NonEmptyListSer<Coordinates>,
    val mediumLevelTrips: NonEmptyListSer<Coordinates>,
    val highLevelTrips: NonEmptyListSer<Coordinates>,
    val professionWorkshops: Map<GameClassName, List<Coordinates>>,
    val startingPoint: Coordinates
)
