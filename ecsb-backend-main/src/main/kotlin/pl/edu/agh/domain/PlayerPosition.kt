package pl.edu.agh.domain

import kotlinx.serialization.Serializable

@Serializable
data class PlayerPosition(val id: PlayerId, val coords: Coordinates, val direction: Direction)
