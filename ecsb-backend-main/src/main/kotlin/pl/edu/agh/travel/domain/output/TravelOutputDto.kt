package pl.edu.agh.travel.domain.output

import kotlinx.serialization.Serializable
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.travel.domain.Range
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.PosInt

@Serializable
data class TravelOutputDto(
    val name: TravelName,
    val time: PosInt,
    val moneyRange: Range<PosInt>,
    val resources: NonEmptyMap<GameResourceName, NonNegInt>,
    val regenTime: TimestampMillis
) {
    companion object {
        fun create(
            name: TravelName,
            time: PosInt,
            moneyRange: Range<PosInt>,
            regenTime: TimestampMillis
        ): (NonEmptyMap<GameResourceName, NonNegInt>) -> TravelOutputDto = { resources ->
            TravelOutputDto(name, time, moneyRange, resources, regenTime)
        }
    }
}
