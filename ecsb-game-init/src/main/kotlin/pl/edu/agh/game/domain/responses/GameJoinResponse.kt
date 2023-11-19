package pl.edu.agh.game.domain.responses

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameSessionId

@Serializable
data class GameJoinResponse(
    val gameToken: String,
    val gameSessionId: GameSessionId
)
