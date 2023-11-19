package pl.edu.agh.travel.domain.out

import arrow.core.Option
import kotlinx.serialization.Serializable
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.travel.domain.Range
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.OptionS
import pl.edu.agh.utils.PosInt

@Serializable
data class GameTravelsView(
    val name: TravelName,
    val time: OptionS<PosInt>,
    val moneyRange: Range<PosInt>,
    val resources: NonEmptyMap<GameResourceName, NonNegInt>
) {
    companion object {
        fun create(
            name: TravelName,
            time: Option<PosInt>,
            moneyRange: Range<PosInt>
        ): (NonEmptyMap<GameResourceName, NonNegInt>) -> GameTravelsView = { resources ->
            GameTravelsView(name, time, moneyRange, resources)
        }
    }
}
