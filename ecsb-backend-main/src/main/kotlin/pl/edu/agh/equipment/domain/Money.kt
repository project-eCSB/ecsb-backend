package pl.edu.agh.equipment.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.utils.NonNegInt

@Serializable
@JvmInline
value class Money(val value: Long) : Comparable<Money> {
    init {
        require(value >= 0) { "Money must be non-negative" }
    }

    constructor(value: NonNegInt) : this(value.value.toLong())

    override fun compareTo(other: Money): Int =
        value.compareTo(other.value)
}
