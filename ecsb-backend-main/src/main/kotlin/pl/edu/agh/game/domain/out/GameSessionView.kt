package pl.edu.agh.game.domain.out

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.`in`.GameClassResourceDto

@Serializable
data class GameSessionView(
    val classResourceRepresentation: List<GameClassResourceDto>,
    val assetUrl: String,
    val gameSessionId: GameSessionId,
    val name: String,
    val shortName: String
)
