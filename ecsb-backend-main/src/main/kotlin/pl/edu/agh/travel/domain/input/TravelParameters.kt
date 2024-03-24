package pl.edu.agh.travel.domain.input

import kotlinx.serialization.Serializable
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.travel.domain.Range
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.PosInt

@Serializable
data class TravelParameters(
    val assets: NonEmptyMap<GameResourceName, NonNegInt>,
    val moneyRange: Range<PosInt>,
    val time: PosInt,
    val regenTime: TimestampMillis
)
