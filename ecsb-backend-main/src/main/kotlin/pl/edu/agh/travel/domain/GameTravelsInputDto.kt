package pl.edu.agh.travel.domain

import arrow.core.Option
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.`in`.Range

data class GameTravelsInputDto(
    val gameSessionId: GameSessionId,
    val travelType: MapDataTypes.Trip,
    val name: TravelName,
    val timeNeeded: Option<Int>,
    val moneyRange: Range<Long>
)
