package pl.edu.agh.domain

import kotlinx.serialization.Serializable

@Serializable
data class PlayerStatus(
    val coords: Coordinates,
    val direction: Direction,
    val className: GameClassName,
    val playerId: PlayerId
)