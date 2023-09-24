package pl.edu.agh.coop.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.serialization.Serializable

@Serializable
data class ResourceDiff<T : Comparable<T>>(val amount: T, val needed: T) {
    fun validate(): Either<String, Unit> =
        if (amount < needed) {
            "Not enough of ".left()
        } else {
            Unit.right()
        }

    companion object {
        operator fun <T : Comparable<T>> invoke(pair: Pair<T, T>): ResourceDiff<T> =
            ResourceDiff(minOf(pair.first, pair.second), pair.second)
    }
}