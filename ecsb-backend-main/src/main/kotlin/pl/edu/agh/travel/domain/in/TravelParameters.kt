package pl.edu.agh.travel.domain.`in`

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.game.domain.`in`.Range
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.OptionS
import pl.edu.agh.utils.PosInt

@Serializable
data class TravelParameters(
    val assets: NonEmptyMap<GameResourceName, NonNegInt>,
    val moneyRange: Range<PosInt>,
    val time: OptionS<PosInt>
)
