package pl.edu.agh.game.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.domain.GameSessionId

@Serializable
data class GameSessionDto(
    val id: GameSessionId,
    val name: String,
    val shortName: String,
    val mapId: SavedAssetsId
)
