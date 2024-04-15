package pl.edu.agh.travel.domain

import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.travel.domain.output.TravelOutputDto
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.PosInt

data class TravelDto(
    val gameSessionId: GameSessionId,
    val travelType: MapDataTypes.Travel,
    val name: TravelName,
    val time: PosInt,
    val moneyRange: Range<PosInt>,
    val regenTime: TimestampMillis
)

typealias Travels = NonEmptyMap<MapDataTypes.Travel, NonEmptyMap<TravelId, TravelOutputDto>>
