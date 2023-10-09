package pl.edu.agh.game.domain.out

import kotlinx.serialization.Serializable
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.`in`.GameClassResourceDto
import pl.edu.agh.game.service.GameAssets
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.travel.domain.TravelId
import pl.edu.agh.travel.domain.out.GameTravelsView
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.PosInt

@Serializable
data class GameSessionView(
    val classResourceRepresentation: NonEmptyMap<GameClassName, GameClassResourceDto>,
    val travels: NonEmptyMap<MapDataTypes.Travel, NonEmptyMap<TravelId, GameTravelsView>>,
    val gameSessionId: GameSessionId,
    val name: String,
    val shortName: String,
    val gameAssets: GameAssets,
    val timeForGame: TimestampMillis,
    val walkingSpeed: PosInt
)
