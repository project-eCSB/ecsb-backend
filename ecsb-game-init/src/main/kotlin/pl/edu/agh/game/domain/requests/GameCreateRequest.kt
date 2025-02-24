package pl.edu.agh.game.domain.requests

import kotlinx.serialization.Serializable
import pl.edu.agh.assets.domain.FileType
import pl.edu.agh.assets.domain.MapDataTypes.Travel
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.game.domain.input.GameClassResourceDto
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.domain.input.TravelParameters
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.PosInt

@Serializable
data class GameCreateRequest(
    val classResourceRepresentation: NonEmptyMap<GameClassName, GameClassResourceDto>,
    val gameName: String,
    val travels: NonEmptyMap<Travel, NonEmptyMap<TravelName, TravelParameters>>,
    val assets: NonEmptyMap<FileType, SavedAssetsId>,
    val timeForGame: TimestampMillis,
    val maxTimeTokens: NonNegInt,
    val walkingSpeed: PosInt,
    val defaultMoney: Money,
    val interactionRadius: PosInt,
    val minPlayersToStart: NonNegInt
)
