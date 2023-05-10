package pl.edu.agh.init.domain.out

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.AssetNumber

@Serializable
data class GameSessionView(
    val classRepresentation: Map<GameClassName, AssetNumber>,
    val assetUrl: String,
    val gameSessionId: GameSessionId,
    val name: String,
    val shortName: String
)
