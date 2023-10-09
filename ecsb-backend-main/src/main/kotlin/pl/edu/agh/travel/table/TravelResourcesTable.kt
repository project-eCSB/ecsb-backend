package pl.edu.agh.travel.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.travel.domain.TravelId
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.NonNegInt.Companion.nonNegDbWrapper
import pl.edu.agh.utils.intWrapper
import pl.edu.agh.utils.stringWrapper

object TravelResourcesTable : Table("GAME_TRAVELS_RESOURCES") {
    val travelId = intWrapper(TravelId::value, ::TravelId)("TRAVEL_ID")
    val classResourceName: Column<GameResourceName> =
        stringWrapper(GameResourceName::value, ::GameResourceName)("CLASS_RESOURCE_NAME")
    val value: Column<NonNegInt> = nonNegDbWrapper("REQUIRED_VALUE")

    fun toDomain(it: ResultRow): Pair<GameResourceName, NonNegInt> = it[classResourceName] to it[value]

    fun domainColumns(): List<Expression<*>> = listOf(classResourceName, value)
}
