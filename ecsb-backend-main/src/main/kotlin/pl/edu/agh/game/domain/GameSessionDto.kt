package pl.edu.agh.game.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.assets.domain.GameAssets
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.PosInt

@Serializable
data class GameSessionDto(
    val id: GameSessionId,
    val name: String,
    val shortName: String,
    val walkingSpeed: PosInt,
    val gameAssets: GameAssets,
    val timeForGame: TimestampMillis,
    val maxPlayerAmount: NonNegInt,
    val interactionRadius: PosInt,
    val maxTimeAmount: NonNegInt,
    val defaultMoney: Money
)
