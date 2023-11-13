package pl.edu.agh.moving

import kotlinx.serialization.Serializable
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.moving.domain.PlayerPosition

@Serializable
data class PlayerPositionWithClass(val className: GameClassName, val playerPosition: PlayerPosition)
