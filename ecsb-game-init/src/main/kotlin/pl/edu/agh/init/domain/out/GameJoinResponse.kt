package pl.edu.agh.init.domain.out

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameSessionId

@Serializable
data class GameJoinResponse(val gameToken: String, val gameSessionId: GameSessionId)
