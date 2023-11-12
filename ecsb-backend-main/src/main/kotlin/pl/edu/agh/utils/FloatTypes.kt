package pl.edu.agh.utils

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.PosInt.Companion.pos
import kotlin.math.roundToInt

@JvmInline
@Serializable
value class Percentile(val value: Float) : Comparable<Percentile> {
    init {
        require(value in 0.0..1.0)
    }

    override fun compareTo(other: Percentile): Int {
        return this.value.compareTo(other.value)
    }

    operator fun times(reward: PosInt): NonNegFloat {
        return NonNegFloat(this.value * reward.value)
    }

    fun splitValue(value: PosInt): SplitValue {
        val firstReward = (this * value).roundToNonNegInt()
        val secondReward = (value.value - firstReward.value).nonNeg

        return SplitValue(firstReward, secondReward)
    }

    fun invert(): Percentile = Percentile(1f - value)
}

@JvmInline
@Serializable
value class NonNegFloat(val value: Float) {
    init {
        require(value >= 0)
    }

    operator fun times(other: PosInt): NonNegFloat =
        NonNegFloat(this.value * other.value)

    fun roundToNonNegInt(): NonNegInt =
        this.value.roundToInt().nonNeg
}
