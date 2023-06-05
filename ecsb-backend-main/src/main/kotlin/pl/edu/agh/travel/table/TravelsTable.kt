package pl.edu.agh.travel.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.travel.domain.TravelId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.intWrapper
import pl.edu.agh.utils.stringWrapper

object TravelsTable : Table("GAME_TRAVELS") {
    val id = intWrapper(TravelId::value, ::TravelId)("ID")
    val gameSessionId = intWrapper(GameSessionId::value, ::GameSessionId)("GAME_SESSION_ID")
    val travelType: Column<MapDataTypes.Trip> =
        stringWrapper(MapDataTypes.Trip::dataValue, MapDataTypes.Trip::fromString)("TRAVEL_TYPE")
    val name = stringWrapper(TravelName::value, ::TravelName)("TRAVEL_NAME")
    val timeNeeded = integer("TIME_NEEDED").nullable()
    val moneyMin = integer("MONEY_MIN")
    val moneyMax = integer("MONEY_MAX")
}
