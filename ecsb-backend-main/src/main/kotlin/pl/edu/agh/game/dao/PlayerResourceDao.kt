package pl.edu.agh.game.dao

import arrow.core.*
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.mapOrAccumulate
import arrow.core.raise.option
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.case
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import pl.edu.agh.domain.*
import pl.edu.agh.game.table.GameSessionUserClassesTable
import pl.edu.agh.game.table.GameUserTable
import pl.edu.agh.game.table.PlayerResourceTable
import pl.edu.agh.time.table.PlayerTimeTokenTable
import pl.edu.agh.travel.domain.TravelId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.table.TravelResourcesTable
import pl.edu.agh.travel.table.TravelsTable
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.literal
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg

object PlayerResourceDao {
    private val logger by LoggerDelegate()
    fun updateResources(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        equipmentChanges: PlayerEquipmentChanges
    ): DB<Either<NonEmptyList<String>, Unit>> = {
        val allResourcesToBeUpdated: Map<GameResourceName, Ior<NonNegInt, NonNegInt>> =
            equipmentChanges.resources.map { (resourceName, change) ->
                val (addition, deletion) = change
                Ior.fromNullables(addition, deletion).toOption().map {
                    resourceName to it
                }
            }.flattenOption().toMap()

        val resourcesValuesClause =
            allResourcesToBeUpdated.fold(CaseWhen<NonNegInt>(null)) { initialCase, (resourceName, iorChange) ->
                val updatedValue = iorChange.fold(
                    { left -> PlayerResourceTable.value + left },
                    { right -> PlayerResourceTable.value - right },
                    { left, right -> PlayerResourceTable.value + left - right }
                )

                val updatedValueWithCase = case(null).When(GreaterEqOp(updatedValue, 0.nonNeg.literal()), updatedValue)
                    .Else(PlayerResourceTable.value)

                initialCase.When(
                    PlayerResourceTable.resourceName eq resourceName,
                    updatedValueWithCase
                )
            }.let {
                CaseWhenElse(it, PlayerResourceTable.value)
            }

        either {
            val oldPlayerResourceTable = PlayerResourceTable.alias("old_player_resource")
            val updateResult = PlayerResourceTable.updateReturning(
                {
                    (PlayerResourceTable.gameSessionId eq gameSessionId) and
                            (PlayerResourceTable.playerId eq playerId) and
                            (PlayerResourceTable.resourceName.inList(allResourcesToBeUpdated.keys))
                },
                from = oldPlayerResourceTable,
                joinColumns = listOf(
                    PlayerResourceTable.gameSessionId,
                    PlayerResourceTable.playerId,
                    PlayerResourceTable.resourceName
                ),
                updateObjects = UpdateObject(PlayerResourceTable.value, resourcesValuesClause),
                returningNew = mapOf("resource_name" to PlayerResourceTable.resourceName)
            )
            mapOrAccumulate(updateResult) { (returningNew, returningBoth) ->
                option {
                    val resourceName = returningNew["resource_name"].toOption().bind()
                    val valueChanges =
                        returningBoth[PlayerResourceTable.value.name].toOption().bind()
                    val changes = allResourcesToBeUpdated[resourceName].toOption().bind()

                    changes.fold(
                        { _ -> true },
                        { _ -> (valueChanges.after as NonNegInt) < (valueChanges.before as NonNegInt) },
                        { addition, deletion -> if (addition >= deletion) true else (valueChanges.after as NonNegInt) < (valueChanges.before as NonNegInt) }
                    )
                }
                    .toEither { "Internal server error (Error with query execution)" }
                    .flatMap { checkValue ->
                        if (!checkValue) {
                            "Too little ${returningNew["resource_name"]}".left()
                        } else {
                            Unit.right()
                        }
                    }
                    .bind()
            }

            val timeDiff = equipmentChanges.time.map(NonNegInt::value).diff()
            if (timeDiff > 0) {
                raise(nonEmptyListOf("Cannot add time here"))
            } else if (timeDiff == 0) {
                Unit
            } else {
                val affectedRows =
                    PlayerTimeTokenTable.decreasePlayerTimeTokensQuery(gameSessionId, playerId, PosInt(-timeDiff))

                raiseWhen(affectedRows.value != -timeDiff) { nonEmptyListOf("Not enough time") }
            }

            val updatedMoney = equipmentChanges.money.addToColumn(GameUserTable.money)
            val updatedMoneyWithCase =
                case(null).When(GreaterEqOp(updatedMoney, 0.nonNeg.literal()), updatedMoney).Else(GameUserTable.money)

            val oldGameUser = GameUserTable.alias("old_game_user")
            GameUserTable.updateReturning(
                {
                    (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId)
                },
                from = oldGameUser,
                joinColumns = listOf(GameUserTable.gameSessionId, GameUserTable.playerId),
                updateObjects = UpdateObject(GameUserTable.money, updatedMoneyWithCase),
                returningNew = mapOf<String, Column<String>>()
            ).map { (_, returningBoth) ->
                either<NonEmptyList<String>, Unit> {
                    val maybeMoneyChanges =
                        returningBoth[GameUserTable.money.name].toOption()
                    ensure(maybeMoneyChanges.isSome()) { nonEmptyListOf("Couldn't get money from query") }
                    maybeMoneyChanges.map { moneyChanges ->
                        ensure(
                            !(moneyChanges.before == moneyChanges.after &&
                                    equipmentChanges.money.map(Money::value).diff() != 0)
                        ) {
                            nonEmptyListOf("Too little money")
                        }
                    }
                }.onLeft { logger.error("Couldn't do this exchange because $it") }
                    .onLeft { logger.error("Couldn't get info about money from: \n$returningBoth") }
                    .onRight { logger.info("Successfully updated money") }
            }.bindAll()
        }.onLeft { rollback() }.onLeft { logger.error("Couldn't update equipment due to $it") }
            .onRight { logger.info("Successfully updated player equipment $playerId in $gameSessionId") }.map { }
    }

    fun getUserEquipment(gameSessionId: GameSessionId, playerId: PlayerId): Option<PlayerEquipment> =
        getUsersEquipments(gameSessionId, listOf(playerId))[playerId].toOption()

    fun getUsersEquipments(
        gameSessionId: GameSessionId,
        players: List<PlayerId>
    ): Map<PlayerId, PlayerEquipment> {
        val playersBasicEquipment = GameUserTable
            .slice(GameUserTable.playerId, GameUserTable.money)
            .select {
                (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId inList players)
            }.associate {
                it[GameUserTable.playerId] to it[GameUserTable.money]
            }

        val resources = PlayerResourceTable.slice(
            PlayerResourceTable.playerId,
            PlayerResourceTable.resourceName,
            PlayerResourceTable.value
        ).select {
            (PlayerResourceTable.gameSessionId eq gameSessionId) and (PlayerResourceTable.playerId inList players)
        }.groupBy(
            { it[PlayerResourceTable.playerId] },
            { it[PlayerResourceTable.resourceName] to it[PlayerResourceTable.value] }
        ).mapValues { (_, value) ->
            value.toNonEmptyMapOrNone()
        }.filterOption()

        return playersBasicEquipment.zip(resources) { _, money, maybeResources ->
            PlayerEquipment(money, maybeResources)
        }
    }

    fun insertUserResources(gameSessionId: GameSessionId, playerId: PlayerId) {
        GameSessionUserClassesTable
            .slice(GameSessionUserClassesTable.resourceName)
            .select {
                GameSessionUserClassesTable.gameSessionId eq gameSessionId
            }.map { it[GameSessionUserClassesTable.resourceName] }.forEach { gameResourceName ->
                PlayerResourceTable.insert {
                    it[PlayerResourceTable.gameSessionId] = gameSessionId
                    it[PlayerResourceTable.playerId] = playerId
                    it[PlayerResourceTable.resourceName] = gameResourceName
                    it[PlayerResourceTable.value] = 0.nonNeg
                }
            }
    }

    fun getPlayerWorkshopData(
        gameSessionId: GameSessionId,
        playerId: PlayerId
    ): Option<Triple<GameResourceName, PosInt, PosInt>> = GameUserTable.join(
        GameSessionUserClassesTable,
        JoinType.INNER
    ) {
        (GameUserTable.gameSessionId eq GameSessionUserClassesTable.gameSessionId) and (GameUserTable.className eq GameSessionUserClassesTable.className)
    }.slice(
        GameSessionUserClassesTable.resourceName,
        GameSessionUserClassesTable.unitPrice,
        GameSessionUserClassesTable.maxProduction
    ).select {
        (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId)
    }.firstOrNone().map {
        Triple(
            it[GameSessionUserClassesTable.resourceName],
            it[GameSessionUserClassesTable.unitPrice],
            it[GameSessionUserClassesTable.maxProduction]
        )
    }

    fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        resourceName: GameResourceName,
        quantity: PosInt,
        unitPrice: PosInt,
        timeNeeded: NonNegInt
    ): DB<Either<NonEmptyList<String>, Unit>> = {
        updateResources(
            gameSessionId,
            playerId,
            PlayerEquipmentChanges(
                money = ChangeValue(Money(0), Money((quantity * unitPrice).value.toLong())),
                resources = nonEmptyMapOf(resourceName to ChangeValue(quantity.toNonNeg(), 0.nonNeg)),
                time = ChangeValue(0.nonNeg, timeNeeded)
            )
        )()
    }

    fun getCityCosts(travelId: TravelId): NonEmptyMap<GameResourceName, NonNegInt> =
        TravelResourcesTable
            .slice(TravelResourcesTable.domainColumns())
            .select { (TravelResourcesTable.travelId eq travelId) }
            .associate { TravelResourcesTable.toDomain(it) }.toNonEmptyMapUnsafe()

    fun getTravelData(
        gameSessionId: GameSessionId,
        travelName: TravelName
    ): Option<Tuple4<PosInt, PosInt, TravelId, PosInt?>> =
        TravelsTable.slice(TravelsTable.moneyMax, TravelsTable.moneyMin, TravelsTable.id, TravelsTable.timeNeeded)
            .select {
                (TravelsTable.gameSessionId eq gameSessionId) and (TravelsTable.name eq travelName)
            }.map {
                Tuple4(
                    it[TravelsTable.moneyMin],
                    it[TravelsTable.moneyMax],
                    it[TravelsTable.id],
                    it[TravelsTable.timeNeeded]
                )
            }.firstOrNone()

    fun conductPlayerTravel(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        cityCosts: NonEmptyMap<GameResourceName, NonNegInt>,
        reward: PosInt,
        time: PosInt?
    ): DB<Either<NonEmptyList<String>, Unit>> = updateResources(
        gameSessionId,
        playerId,
        PlayerEquipmentChanges(
            money = ChangeValue(Money(reward.value.toLong()), Money(0)),
            resources = cityCosts.map.mapValues { (_, value) -> ChangeValue(0.nonNeg, value) }.toNonEmptyMapUnsafe(),
            time = ChangeValue(0.nonNeg, time?.toNonNeg() ?: 0.nonNeg)
        )
    )
}
