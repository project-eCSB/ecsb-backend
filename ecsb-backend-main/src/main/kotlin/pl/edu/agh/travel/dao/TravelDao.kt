package pl.edu.agh.travel.dao

import arrow.core.Option
import arrow.core.filterOption
import arrow.core.raise.option
import arrow.core.toOption
import arrow.core.traverse
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.`in`.Range
import pl.edu.agh.travel.domain.TravelId
import pl.edu.agh.travel.domain.`in`.GameTravelsInputDto
import pl.edu.agh.travel.domain.out.GameTravelsView
import pl.edu.agh.travel.table.TravelResourcesTable
import pl.edu.agh.travel.table.TravelsTable
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonEmptyMap.Companion.fromMapSafe

object TravelDao {

    fun insertTravel(validatedTravel: GameTravelsInputDto, travelResources: Map<GameResourceName, Int>): TravelId {
        val travelId = insertTravel(validatedTravel)

        batchInsertDto(travelId, travelResources.toList())

        return travelId
    }

    private fun insertTravel(validatedTravel: GameTravelsInputDto): TravelId = TravelsTable.insert {
        it[TravelsTable.gameSessionId] = validatedTravel.gameSessionId
        it[TravelsTable.travelType] = validatedTravel.travelType
        it[TravelsTable.name] = validatedTravel.name
        it[TravelsTable.timeNeeded] = validatedTravel.time.getOrNull()
        it[TravelsTable.moneyMin] = validatedTravel.moneyRange.from.toInt()
        it[TravelsTable.moneyMax] = validatedTravel.moneyRange.to.toInt()
    }[TravelsTable.id]

    private fun batchInsertDto(travelId: TravelId, travelResources: List<Pair<GameResourceName, Int>>) {
        TravelResourcesTable.batchInsert(travelResources) { (resourceName, value) ->
            this[TravelResourcesTable.travelId] = travelId
            this[TravelResourcesTable.classResourceName] = resourceName
            this[TravelResourcesTable.value] = value
        }
    }

    fun getTravels(gameSessionId: GameSessionId): Option<NonEmptyMap<MapDataTypes.Travel, NonEmptyMap<TravelId, GameTravelsView>>> =
        option {
            val mainView = TravelsTable.select {
                TravelsTable.gameSessionId eq gameSessionId
            }.map {
                Triple(
                    it[TravelsTable.travelType],
                    it[TravelsTable.id],
                    GameTravelsView.create(
                        it[TravelsTable.name],
                        it[TravelsTable.timeNeeded].toOption(),
                        Range(it[TravelsTable.moneyMin].toLong(), it[TravelsTable.moneyMax].toLong())
                    )
                )
            }

            val travelIds = mainView.map { (_, travelId, _) -> travelId }.toSet()

            val resourcePerTravelId: NonEmptyMap<TravelId, NonEmptyMap<GameResourceName, Int>> =
                TravelResourcesTable.select { TravelResourcesTable.travelId inList travelIds }
                    .groupBy(
                        { it[TravelResourcesTable.travelId] },
                        { it[TravelResourcesTable.classResourceName] to it[TravelResourcesTable.value] }
                    )
                    .mapValues { (_, resources) -> NonEmptyMap.fromListSafe(resources) }
                    .filterOption()
                    .let(::fromMapSafe).bind()

            val mergedTravelsList = mainView.traverse { (travelType, travelId, gameTravelsViewBuilder) ->
                option {
                    val resources = resourcePerTravelId[travelId].toOption().bind()
                    travelType to (travelId to gameTravelsViewBuilder(resources))
                }
            }.bind()

            mergedTravelsList
                .groupBy({ (key, _) -> key }, { (_, value) -> value })
                .mapValues { (_, rest) ->
                    rest.toMap().let(::fromMapSafe)
                }
                .filterOption()
                .let(::fromMapSafe)
                .bind()
        }
}
