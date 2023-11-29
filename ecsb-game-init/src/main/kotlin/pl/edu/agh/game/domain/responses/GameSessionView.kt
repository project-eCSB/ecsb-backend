package pl.edu.agh.game.domain.responses

import kotlinx.serialization.Serializable
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.game.domain.`in`.GameClassResourceDto
import pl.edu.agh.assets.domain.GameAssets
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.travel.domain.TravelId
import pl.edu.agh.travel.domain.out.GameTravelsView
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
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
    val walkingSpeed: PosInt,
    val maxTimeAmount: NonNegInt,
    val defaultMoney: Money,
    val interactionRadius: PosInt,
    val maxPlayerAmount: NonNegInt
)
