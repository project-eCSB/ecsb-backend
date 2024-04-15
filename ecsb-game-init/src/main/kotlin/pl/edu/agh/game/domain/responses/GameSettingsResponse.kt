package pl.edu.agh.game.domain.responses

import kotlinx.serialization.Serializable
import pl.edu.agh.assets.domain.FileType
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.game.domain.input.GameClassResourceDto
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.travel.domain.Travels
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.PosInt

@Serializable
data class GameSettingsResponse(
    val classResourceRepresentation: NonEmptyMap<GameClassName, GameClassResourceDto>,
    val travels: Travels,
    val gameSessionId: GameSessionId,
    val name: String,
    val shortName: String,
    val gameAssets: NonEmptyMap<FileType, SavedAssetsId>,
    val timeForGame: TimestampMillis,
    val walkingSpeed: PosInt,
    val maxTimeTokens: NonNegInt,
    val defaultMoney: Money,
    val interactionRadius: PosInt,
    val minPlayersToStart: NonNegInt
)
