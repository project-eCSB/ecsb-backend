package pl.edu.agh.game.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameResourceName

@Serializable
data class GameResourceDto(val name: GameResourceName, val value: Int)
