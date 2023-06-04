package pl.edu.agh.game.domain.`in`

import kotlinx.serialization.Serializable

@Serializable
data class Range<T: Number>(val from: T, val to: T)
