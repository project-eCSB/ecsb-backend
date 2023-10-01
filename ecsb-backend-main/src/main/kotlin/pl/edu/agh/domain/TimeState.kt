package pl.edu.agh.domain

import kotlinx.serialization.Serializable
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.PosInt

@Serializable
data class TimeState(val actual: NonNegInt, val max: PosInt)
