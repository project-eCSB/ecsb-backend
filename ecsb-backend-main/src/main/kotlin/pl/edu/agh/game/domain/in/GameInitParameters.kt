package pl.edu.agh.game.domain.`in`

import kotlinx.serialization.Serializable
import pl.edu.agh.assets.domain.MapDataTypes.Trip
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.OptionS

@Serializable
data class GameInitParameters(
    val classResourceRepresentation: NonEmptyMap<GameClassName, GameClassResourceDto>,
    val gameName: String,
    val mapId: OptionS<SavedAssetsId>,
    val travels: NonEmptyMap<Trip, Range<Long>>
)
