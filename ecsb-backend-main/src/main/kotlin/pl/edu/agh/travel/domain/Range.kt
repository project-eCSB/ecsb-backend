package pl.edu.agh.travel.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.utils.Randomable

@Serializable
data class Range<T>(val from: T, val to: T) where T : Comparable<T> {
    fun random(randomable: Randomable<T>): T = randomable.nextRandomInRange((from..to))
}
