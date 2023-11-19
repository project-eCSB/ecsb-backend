package pl.edu.agh.travel.dao

import arrow.core.*
import arrow.core.raise.option
import org.jetbrains.exposed.sql.*
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.equipment.domain.GameResourceName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.travel.domain.Range
import pl.edu.agh.travel.domain.TravelId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.domain.`in`.GameTravelsInputDto
import pl.edu.agh.travel.domain.out.GameTravelsView
import pl.edu.agh.travel.table.TravelResourcesTable
import pl.edu.agh.travel.table.TravelsTable
import pl.edu.agh.utils.*

object TravelDao {

    fun insertTravel(
        validatedTravel: GameTravelsInputDto,
        travelResources: Map<GameResourceName, NonNegInt>
    ) {
        val travelId = insertTravel(validatedTravel)

        TravelResourcesTable.batchInsert(travelResources.toList()) { (resourceName, value) ->
            this[TravelResourcesTable.travelId] = travelId
            this[TravelResourcesTable.classResourceName] = resourceName
            this[TravelResourcesTable.value] = value
        }
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
            val mainView = TravelsTable.slice(
                TravelsTable.travelType,
                TravelsTable.id,
                TravelsTable.name,
                TravelsTable.timeNeeded,
                TravelsTable.moneyMin,
                TravelsTable.moneyMax
            ).select {
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
                TravelResourcesTable.slice(
                    TravelResourcesTable.travelId,
                    TravelResourcesTable.classResourceName,
                    TravelResourcesTable.value
                ).select { TravelResourcesTable.travelId inList travelIds }
                    .groupBy(
                        { it[TravelResourcesTable.travelId] },
                        { it[TravelResourcesTable.classResourceName] to it[TravelResourcesTable.value] }
                    )
                    .mapValues { (_, resources) -> resources.toNonEmptyMapOrNone() }
                    .filterOption().toNonEmptyMapOrNone().bind()

            val mergedTravelsList = mainView.traverse { (travelType, travelId, gameTravelsViewBuilder) ->
                option {
                    val resources = resourcePerTravelId[travelId].toOption().bind()
                    travelType to (travelId to gameTravelsViewBuilder(resources))
                }
            }.bind()

            mergedTravelsList
                .groupBy({ (key, _) -> key }, { (_, value) -> value })
                .mapValues { (_, rest) ->
                    rest.toNonEmptyMapOrNone()
                }
                .filterOption()
                .toNonEmptyMapOrNone()
                .bind()
        }

    fun getTravelCostsByName(
        gameSessionId: GameSessionId,
        travelName: TravelName
    ): Option<NonEmptyMap<GameResourceName, NonNegInt>> =
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
                TravelResourcesTable.classResourceName,
                TravelResourcesTable.value
            )
            .select {
                (TravelsTable.gameSessionId eq gameSessionId) and (TravelsTable.name eq travelName)
            }.map {
                it[TravelResourcesTable.classResourceName] to it[TravelResourcesTable.value]
            }.toNonEmptyMapOrNone()

    fun getTravelName(
        gameSessionId: GameSessionId,
        travelName: TravelName
    ): Option<TravelName> =
        TravelsTable.select {
            (TravelsTable.gameSessionId eq gameSessionId) and (TravelsTable.name eq travelName)
        }.map { it[TravelsTable.name] }.firstOrNone()

    fun getTravelTimeCost(
        gameSessionId: GameSessionId,
        travelName: TravelName
    ): Option<NonNegInt> =
        TravelsTable.select {
            (TravelsTable.gameSessionId eq gameSessionId) and (TravelsTable.name eq travelName)
        }.map { it[TravelsTable.timeNeeded] }.firstOrNone().flatMap { it.toOption() }.map { it.toNonNeg() }

    fun getTravelData(
        gameSessionId: GameSessionId,
        travelName: TravelName
    ): Option<GameTravelsView> =
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
                (TravelsTable.gameSessionId eq gameSessionId) and (TravelsTable.name eq travelName)
            }
            .map {
                Triple(
                    it[TravelsTable.name],
                    it[TravelsTable.timeNeeded].toOption(),
                    Range(it[TravelsTable.moneyMin], it[TravelsTable.moneyMax])
                ) to (it[TravelResourcesTable.classResourceName] to it[TravelResourcesTable.value])
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, value) ->
                value.toNonEmptyMapOrNone()
            }
            .entries
            .map { (key, maybeValue) ->
                maybeValue.map {
                    (GameTravelsView.Companion::create::tupled)(key)(it)
                }
            }.first()
}
