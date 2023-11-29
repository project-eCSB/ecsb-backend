package pl.edu.agh.game.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId

@Serializable
data class PlayerResult(val playerId: PlayerId, val money: Long)
