package pl.edu.agh.game.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.assets.domain.FileType
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.PosInt

@Serializable
data class GameSessionDto(
    val id: GameSessionId,
    val name: String,
    val shortName: String,
    val walkingSpeed: PosInt,
    val gameAssets: NonEmptyMap<FileType, SavedAssetsId>,
    val timeForGame: TimestampMillis,
    val minPlayersToStart: NonNegInt,
    val interactionRadius: PosInt,
    val maxTimeTokens: NonNegInt,
    val defaultMoney: Money
)
