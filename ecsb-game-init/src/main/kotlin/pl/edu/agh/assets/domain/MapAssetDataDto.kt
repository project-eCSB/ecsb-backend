package pl.edu.agh.assets.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.moving.domain.Coordinates
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.utils.NonEmptyListS

@Serializable
data class MapAssetDataDto(
    val lowLevelTravels: NonEmptyListS<Coordinates>,
    val mediumLevelTravels: NonEmptyListS<Coordinates>,
    val highLevelTravels: NonEmptyListS<Coordinates>,
    val professionWorkshops: Map<GameClassName, List<Coordinates>>,
    val startingPoint: Coordinates
)
