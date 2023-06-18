package pl.edu.agh.utils

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*

@JvmInline
@Serializable
value class PosInt(val value: Int) {
    init {
        require(value > 0)
    }

    fun toNonNeg(): NonNegInt = NonNegInt(value)

    operator fun times(other: PosInt): PosInt =
        PosInt(other.value * value)

    companion object {
        fun Table.posIntWrapper(name: String): Column<PosInt> =
            this.intWrapper(PosInt::value, ::PosInt)(name)

        val Int.pos: PosInt
            get() = PosInt(this)
    }
}

@JvmInline
@Serializable
value class NonNegInt(val value: Int) {
    init {
        require(value >= 0)
    }

    companion object {
        fun Table.nonNegDbWrapper(name: String): Column<NonNegInt> =
            this.intWrapper(NonNegInt::value, ::NonNegInt)(name)

        val Int.nonNeg: NonNegInt
            get() = NonNegInt(this)
    }
}
