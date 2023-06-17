package pl.edu.agh.travel.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.travel.domain.TravelId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.PosInt.Companion.posIntWrapper
import pl.edu.agh.utils.intWrapper
import pl.edu.agh.utils.stringWrapper

object TravelsTable : Table("GAME_TRAVELS") {
    val id = intWrapper(TravelId::value, ::TravelId)("ID").autoIncrement()
    val gameSessionId = intWrapper(GameSessionId::value, ::GameSessionId)("GAME_SESSION_ID")
    val travelType: Column<MapDataTypes.Travel> =
        stringWrapper(MapDataTypes.Travel::dataValue, MapDataTypes.Travel::fromString)("TRAVEL_TYPE")
    val name = stringWrapper(TravelName::value, ::TravelName)("TRAVEL_NAME")
    val timeNeeded = posIntWrapper("TIME_NEEDED").nullable()
    val moneyMin = posIntWrapper("MONEY_MIN")
    val moneyMax = posIntWrapper("MONEY_MAX")
}
