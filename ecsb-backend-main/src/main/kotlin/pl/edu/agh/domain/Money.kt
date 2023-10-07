package pl.edu.agh.domain

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Money(val value: Long) : Comparable<Money> {
    init {
        require(value >= 0) { "Money must be non-negative" }
    }

    override fun compareTo(other: Money): Int =
        value.compareTo(other.value)
}
