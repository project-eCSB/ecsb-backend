package pl.edu.agh.travel.domain.`in`

import arrow.core.Option
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.`in`.Range
import pl.edu.agh.travel.domain.TravelName

data class GameTravelsInputDto(
    val gameSessionId: GameSessionId,
    val travelType: MapDataTypes.Trip,
    val name: TravelName,
    val time: Option<Int>,
    val moneyRange: Range<Long>
)
