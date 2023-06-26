package pl.edu.agh.travel.dao

import arrow.core.*
import arrow.core.raise.option
import org.jetbrains.exposed.sql.*
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.`in`.Range
import pl.edu.agh.travel.domain.TravelId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.domain.`in`.GameTravelsInputDto
import pl.edu.agh.travel.domain.out.GameTravelsView
import pl.edu.agh.travel.domain.out.GameTravelsView.Companion.create
import pl.edu.agh.travel.table.TravelResourcesTable
import pl.edu.agh.travel.table.TravelsTable
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonEmptyMap.Companion.fromMapSafe
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.toNonEmptyMapOrNone
import pl.edu.agh.utils.tupled

object TravelDao {

    fun insertTravel(
        validatedTravel: GameTravelsInputDto,
        travelResources: Map<GameResourceName, NonNegInt>
    ): TravelId {
        val travelId = insertTravel(validatedTravel)

        TravelResourcesTable.batchInsert(travelResources.toList()) { (resourceName, value) ->
            this[TravelResourcesTable.travelId] = travelId
            this[TravelResourcesTable.classResourceName] = resourceName
            this[TravelResourcesTable.value] = value
        }

        return travelId
    }

    private fun insertTravel(validatedTravel: GameTravelsInputDto): TravelId = TravelsTable.insert {
        it[TravelsTable.gameSessionId] = validatedTravel.gameSessionId
        it[TravelsTable.travelType] = validatedTravel.travelType
        it[TravelsTable.name] = validatedTravel.name
        it[TravelsTable.timeNeeded] = validatedTravel.time.getOrNull()
        it[TravelsTable.moneyMin] = validatedTravel.moneyRange.from
        it[TravelsTable.moneyMax] = validatedTravel.moneyRange.to
    }[TravelsTable.id]

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
                        Range(it[TravelsTable.moneyMin], it[TravelsTable.moneyMax])
                    )
                )
            }

            val travelIds = mainView.map { (_, travelId, _) -> travelId }.toSet()

            val resourcePerTravelId: NonEmptyMap<TravelId, NonEmptyMap<GameResourceName, NonNegInt>> =
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

    fun getTravelByName(gameSessionId: GameSessionId, travelName: TravelName): Option<GameTravelsView> =
        getTravelByNames(gameSessionId, nonEmptySetOf(travelName)).map { it.first() }

    fun getTravelByNames(
        gameSessionId: GameSessionId,
        names: NonEmptySet<TravelName>
    ): Option<NonEmptySet<GameTravelsView>> =
        TravelsTable
            .join(
                TravelResourcesTable,
                JoinType.INNER,
                additionalConstraint = {
                    TravelsTable.id eq TravelResourcesTable.travelId
                }
            )
            .slice(
                TravelsTable.name,
                TravelsTable.timeNeeded,
                TravelsTable.moneyMin,
                TravelsTable.moneyMax,
                TravelResourcesTable.classResourceName,
                TravelResourcesTable.value
            )
            .select {
                (TravelsTable.gameSessionId eq gameSessionId) and (TravelsTable.name inList names)
            }
            .map {
                Triple(
                    it[TravelsTable.name],
                    it[TravelsTable.timeNeeded].toOption(),
                    Range(it[TravelsTable.moneyMin], it[TravelsTable.moneyMax])
                ) to (
                    it[TravelResourcesTable.classResourceName] to
                        it[TravelResourcesTable.value]
                    )
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, value) ->
                value.toNonEmptyMapOrNone()
            }
            .entries
            .map { (key, maybeValue) ->
                maybeValue.map {
                    (::create::tupled)(key)(it)
                }
            }.flattenOption().toNonEmptySetOrNone()
}
