package pl.edu.agh.game.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.service.GameAssets
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.utils.PosInt

@Serializable
data class GameSessionDto(
    val id: GameSessionId,
    val name: String,
    val shortName: String,
    val walkingSpeed: PosInt,
    val gameAssets: GameAssets,
    val timeForGame: TimestampMillis
)
