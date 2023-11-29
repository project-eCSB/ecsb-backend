package pl.edu.agh.moving.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.domain.GameClassName

@Serializable
data class PlayerStatus(
    val coords: Coordinates,
    val direction: Direction,
    val className: GameClassName,
    val playerId: PlayerId
)
