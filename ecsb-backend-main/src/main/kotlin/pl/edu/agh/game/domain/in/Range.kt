package pl.edu.agh.game.domain.`in`

import kotlinx.serialization.Serializable

@Serializable
data class Range<T>(val from: T, val to: T)
