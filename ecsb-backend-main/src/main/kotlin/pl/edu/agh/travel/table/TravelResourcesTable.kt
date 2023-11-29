package pl.edu.agh.travel.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.travel.domain.TravelId
import pl.edu.agh.utils.Domainable
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.NonNegInt.Companion.nonNegDbWrapper
import pl.edu.agh.utils.intWrapper
import pl.edu.agh.utils.stringWrapper

object TravelResourcesTable : Table("GAME_TRAVELS_RESOURCES"), Domainable<Pair<GameResourceName, NonNegInt>> {
    val travelId = intWrapper(TravelId::value, ::TravelId)("TRAVEL_ID")
    val classResourceName: Column<GameResourceName> =
        stringWrapper(GameResourceName::value, ::GameResourceName)("CLASS_RESOURCE_NAME")
    val value: Column<NonNegInt> = nonNegDbWrapper("REQUIRED_VALUE")

    override fun toDomain(resultRow: ResultRow): Pair<GameResourceName, NonNegInt> =
        resultRow[classResourceName] to resultRow[value]

    override val domainColumns: List<Expression<*>> = listOf(classResourceName, value)
}
