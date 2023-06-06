package pl.edu.agh.travel.domain.out

import arrow.core.Option
import kotlinx.serialization.Serializable
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.game.domain.`in`.Range
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.OptionS

@Serializable
data class GameTravelsView(
    val name: TravelName,
    val time: OptionS<Int>,
    val moneyRange: Range<Long>,
    val resources: NonEmptyMap<GameResourceName, Int>
) {
    companion object {
        fun create(
            name: TravelName,
            time: Option<Int>,
            moneyRange: Range<Long>
        ): (NonEmptyMap<GameResourceName, Int>) -> GameTravelsView = { resources ->
            GameTravelsView(name, time, moneyRange, resources)
        }
    }
}
