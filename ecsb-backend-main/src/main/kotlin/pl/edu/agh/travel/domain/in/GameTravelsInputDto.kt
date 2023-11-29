package pl.edu.agh.travel.domain.`in`

import arrow.core.Option
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.travel.domain.Range
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.PosInt

data class GameTravelsInputDto(
    val gameSessionId: GameSessionId,
    val travelType: MapDataTypes.Travel,
    val name: TravelName,
    val time: Option<PosInt>,
    val moneyRange: Range<PosInt>
)
