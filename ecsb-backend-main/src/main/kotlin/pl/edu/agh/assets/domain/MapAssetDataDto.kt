package pl.edu.agh.assets.domain

import arrow.core.NonEmptyList
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.utils.NelCoordsSerializer

@Serializable
data class MapAssetDataDto(
    @Serializable(with = NelCoordsSerializer::class) val lowLevelTrips: NonEmptyList<Coordinates>,
    @Serializable(with = NelCoordsSerializer::class) val mediumLevelTrips: NonEmptyList<Coordinates>,
    @Serializable(with = NelCoordsSerializer::class) val highLevelTrips: NonEmptyList<Coordinates>,
    val professionWorkshops: Map<GameClassName, List<Coordinates>>,
    val startingPoint: Coordinates
)
