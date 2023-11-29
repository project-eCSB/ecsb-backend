package pl.edu.agh.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.serialization.Serializable

@Serializable
data class AmountDiff<T : Comparable<T>>(val amount: T, val needed: T) {
    fun validate(): Either<String, Unit> =
        if (amount < needed) {
            "Not enough of ".left()
        } else {
            Unit.right()
        }

    companion object {
        operator fun <T : Comparable<T>> invoke(pair: Pair<T, T>): AmountDiff<T> =
            AmountDiff(minOf(pair.first, pair.second), pair.second)
    }
}
