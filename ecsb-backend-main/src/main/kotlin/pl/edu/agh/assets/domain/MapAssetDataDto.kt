package pl.edu.agh.assets.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.utils.NonEmptyListS

@Serializable
data class MapAssetDataDto(
    val lowLevelTrips: NonEmptyListS<Coordinates>,
    val mediumLevelTrips: NonEmptyListS<Coordinates>,
    val highLevelTrips: NonEmptyListS<Coordinates>,
    val professionWorkshops: Map<GameClassName, List<Coordinates>>,
    val startingPoint: Coordinates
)
