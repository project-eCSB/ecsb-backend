package pl.edu.agh.game.domain.out

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.SessionClassDto

@Serializable
data class GameSessionView(
    val classRepresentation: Map<GameClassName, SessionClassDto>,
    val assetUrl: String,
    val gameSessionId: GameSessionId,
    val name: String,
    val shortName: String
)
