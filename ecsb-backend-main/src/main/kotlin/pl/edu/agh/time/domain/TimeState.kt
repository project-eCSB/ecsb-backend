package pl.edu.agh.time.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.PosInt

@Serializable
data class TimeState(val actual: NonNegInt, val max: PosInt) {
    fun isReady(): Boolean = actual == max.toNonNeg()
}
