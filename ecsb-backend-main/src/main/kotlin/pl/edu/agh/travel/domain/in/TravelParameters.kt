package pl.edu.agh.travel.domain.`in`

import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.game.domain.`in`.Range
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.OptionS

@Serializable
data class TravelParameters(
    val assets: NonEmptyMap<GameResourceName, Int>,
    val moneyRange: Range<Long>,
    val time: OptionS<Int>
)
