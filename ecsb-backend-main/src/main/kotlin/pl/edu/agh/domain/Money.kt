package pl.edu.agh.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.utils.PosInt

@Serializable
@JvmInline
value class Money(val value: Long) : Comparable<Money> {
    init {
        require(value >= 0) { "Money must be non-negative" }
    }

    constructor(value: PosInt) : this(value.value.toLong())

    override fun compareTo(other: Money): Int =
        value.compareTo(other.value)
}
