package pl.edu.agh.game.domain.out

import kotlinx.serialization.Serializable
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.domain.GameClassName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.`in`.GameClassResourceDto
import pl.edu.agh.utils.NonEmptyMap

@Serializable
data class GameSessionView(
    val classResourceRepresentation: NonEmptyMap<GameClassName, GameClassResourceDto>,
    val gameSessionId: GameSessionId,
    val name: String,
    val shortName: String,
    val mapId: SavedAssetsId
)
