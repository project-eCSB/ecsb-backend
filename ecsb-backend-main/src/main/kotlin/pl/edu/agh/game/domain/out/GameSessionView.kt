package pl.edu.agh.game.domain.out

import kotlinx.serialization.Serializable
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.`in`.GameClassResourceDto
import pl.edu.agh.game.service.GameAssets
import pl.edu.agh.travel.domain.TravelId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.domain.`in`.TravelParameters
import pl.edu.agh.travel.domain.out.GameTravelsView
import pl.edu.agh.utils.NonEmptyMap

@Serializable
data class GameSessionView(
    val classResourceRepresentation: NonEmptyMap<GameClassName, GameClassResourceDto>,
    val trips: NonEmptyMap<MapDataTypes.Trip, NonEmptyMap<TravelId, GameTravelsView>>,
    val gameSessionId: GameSessionId,
    val name: String,
    val shortName: String,
    val gameAssets: GameAssets
)
