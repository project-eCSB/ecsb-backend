package pl.edu.agh.move.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.PlayerId

@Serializable
data class PlayerPosition(val id: PlayerId, val coords: Coordinates)
