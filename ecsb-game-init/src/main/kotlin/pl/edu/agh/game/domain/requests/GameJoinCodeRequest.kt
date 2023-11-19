package pl.edu.agh.game.domain.requests

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId

@Serializable
data class GameJoinCodeRequest(
    val gameCode: String,
    val playerId: PlayerId
)
