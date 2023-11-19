package pl.edu.agh.moving.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.PlayerId

@Serializable
data class PlayerPosition(val id: PlayerId, val coords: Coordinates, val direction: Direction)
