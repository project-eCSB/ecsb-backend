package pl.edu.agh.game.dao

import arrow.core.*
import arrow.core.NonEmptyList
import arrow.core.raise.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.case
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.domain.*
import pl.edu.agh.game.table.GameSessionUserClassesTable
import pl.edu.agh.game.table.GameUserTable
import pl.edu.agh.game.table.PlayerResourceTable
import pl.edu.agh.travel.domain.TravelId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.table.TravelResourcesTable
import pl.edu.agh.travel.table.TravelsTable
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.literal
import pl.edu.agh.utils.NonNegInt.Companion.minus
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.NonNegInt.Companion.plus

object PlayerResourceDao {
    private val logger by LoggerDelegate()
    fun updateResources(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        additions: PlayerEquipment,
        deletions: PlayerEquipment
    ): DB<Either<NonEmptyList<String>, Unit>> = {
        val allResourcesToBeUpdated: Map<GameResourceName, Ior<NonNegInt, NonNegInt>> =
            additions.resources.padZip(deletions.resources).map { (resourceName, change) ->
                val (addition, deletion) = change
                Ior.fromNullables(addition, deletion).toOption().map {
                    resourceName to it
                }
            }.flattenOption().toMap()

        val resourcesValuesClause =
            allResourcesToBeUpdated.fold(CaseWhen<NonNegInt>(null)) { initialCase, (resourceName, iorChange) ->
                val updatedValue =
                    PlayerResourceTable.value + iorChange.fold(
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
                        (PlayerResourceTable.playerId eq playerId) and (
                        PlayerResourceTable.resourceName.inList(
                            allResourcesToBeUpdated.keys
                        )
                        )
                },
                from = oldPlayerResourceTable,
                joinColumns = listOf(
                    PlayerResourceTable.gameSessionId,
                    PlayerResourceTable.playerId,
                    PlayerResourceTable.resourceName
                ),
                updateObjects = listOf(
                    UpdateObject(PlayerResourceTable.value, resourcesValuesClause),
                    UpdateObject(
                        PlayerResourceTable.sharedValue,
                        case().When(resourcesValuesClause.less(PlayerResourceTable.sharedValue), resourcesValuesClause)
                            .Else(PlayerResourceTable.sharedValue)
                    )
                ),
                returningNew = mapOf("resource_name" to PlayerResourceTable.resourceName)
            )
            mapOrAccumulate(updateResult) { (returningNew, returningBoth) ->
                option {
                    val resourceName = returningNew["resource_name"].toOption().bind()
                    val valueChanges = returningBoth[PlayerResourceTable.value.name].toOption().bind()
                    val changes = allResourcesToBeUpdated[resourceName].toOption().bind()

                    changes.fold(
                        { _ -> true },
                        { _ -> valueChanges.after < valueChanges.before },
                        { addition, deletion -> if (addition == deletion) true else valueChanges.after < valueChanges.before }
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

            val updatedMoney = GameUserTable.money + additions.money - deletions.money
            val updatedMoneyWithCase =
                case(null).When(GreaterEqOp(updatedMoney, 0.nonNeg.literal()), updatedMoney).Else(GameUserTable.money)
            val updatedTime = GameUserTable.time + additions.time - deletions.time
            val updatedTimeWithCase =
                case(null).When(GreaterEqOp(updatedTime, 0.nonNeg.literal()), updatedTime).Else(GameUserTable.time)

            val oldGameUser = GameUserTable.alias("old_game_user")
            GameUserTable.updateReturning(
                {
                    (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId)
                },
                from = oldGameUser,
                joinColumns = listOf(GameUserTable.gameSessionId, GameUserTable.playerId),
                updateObjects = listOf(
                    UpdateObject(GameUserTable.money, updatedMoneyWithCase),
                    UpdateObject(
                        GameUserTable.sharedMoney,
                        case(null).When(LessOp(updatedMoneyWithCase, GameUserTable.sharedMoney), updatedMoneyWithCase)
                            .Else(GameUserTable.sharedMoney)
                    ),
                    UpdateObject(GameUserTable.time, updatedTimeWithCase),
                    UpdateObject(
                        GameUserTable.sharedTime,
                        case(null).When(LessOp(updatedTimeWithCase, GameUserTable.sharedTime), updatedTimeWithCase)
                            .Else(GameUserTable.sharedTime)
                    )
                ),
                returningNew = mapOf<String, Column<String>>()
            ).map { (_, returningBoth) ->
                either<NonEmptyList<String>, Unit> {
                    zipOrAccumulate({
                        val maybeMoneyChanges = returningBoth[GameUserTable.money.name].toOption()
                        ensure(maybeMoneyChanges.isSome()) { "Couldn't get money from query" }
                        maybeMoneyChanges.map { moneyChanges ->
                            ensure(!(moneyChanges.before == moneyChanges.after && additions.money != deletions.money)) { "Too little money" }
                        }
                    }, {
                        val maybeTimeChanges = returningBoth[GameUserTable.time.name].toOption()
                        ensure(maybeTimeChanges.isSome()) { "Couldn't get time" }
                        maybeTimeChanges.map { timeChanges ->
                            ensure(!(timeChanges.before == timeChanges.after && additions.time != deletions.time)) { "Too little time" }
                        }
                    }) { _, _ -> }
                }.onLeft { logger.error("Couldn't do this exchange because $it") }
                    .onLeft { logger.error("Couldn't get info about time or money from: \n$returningBoth") }
                    .onRight { logger.info("Successfully updated money and time") }
            }.bindAll()
        }.onLeft { rollback() }.onLeft { logger.error("Couldn't update equipment due to $it") }
            .onRight { logger.info("Successfully updated player equipment $playerId in $gameSessionId") }.map { }
    }

    private fun getPlayerAndSharedResources(
        gameSessionId: GameSessionId,
        playerId: PlayerId
    ): Option<Pair<NonEmptyMap<GameResourceName, NonNegInt>, NonEmptyMap<GameResourceName, NonNegInt>>> {
        val playerResources = mutableMapOf<GameResourceName, NonNegInt>()
        val sharedPlayerResources = mutableMapOf<GameResourceName, NonNegInt>()

        PlayerResourceTable.select {
            (PlayerResourceTable.gameSessionId eq gameSessionId) and (PlayerResourceTable.playerId eq playerId)
        }.forEach {
            val (name, value, sharedValue) = PlayerResourceTable.toSharedTriple(it)
            playerResources[name] = value
            sharedPlayerResources[name] = sharedValue
        }

        return option {
            val playerResourcesOption = NonEmptyMap.fromMapSafe(playerResources).bind()
            val sharedPlayerResourcesOption = NonEmptyMap.fromMapSafe(sharedPlayerResources).bind()

            playerResourcesOption to sharedPlayerResourcesOption
        }
    }

    fun getUserEquipmentByLoginUserId(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId
    ): Option<PlayerEquipmentView> = option {
        val (time, sharedTime, money, sharedMoney, playerId) = GameUserTable.select {
            (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.loginUserId eq loginUserId)
        }.map {
            Tuple5(
                it[GameUserTable.time],
                it[GameUserTable.sharedTime],
                it[GameUserTable.money],
                it[GameUserTable.sharedMoney],
                it[GameUserTable.playerId]
            )
        }.firstOrNone().bind()
        val (resources, sharedResources) = getPlayerAndSharedResources(gameSessionId, playerId).bind()

        PlayerEquipmentView(
            PlayerEquipment(money, time, resources),
            PlayerEquipment(sharedMoney, sharedTime, sharedResources)
        )
    }

    fun insertUserResources(gameSessionId: GameSessionId, playerId: PlayerId) {
        GameSessionUserClassesTable.select {
            GameSessionUserClassesTable.gameSessionId eq gameSessionId
        }.map { it[GameSessionUserClassesTable.resourceName] }.forEach { gameResourceName ->
            PlayerResourceTable.insert {
                it[PlayerResourceTable.gameSessionId] = gameSessionId
                it[PlayerResourceTable.playerId] = playerId
                it[PlayerResourceTable.resourceName] = gameResourceName
                it[PlayerResourceTable.value] = 0.nonNeg
                it[PlayerResourceTable.sharedValue] = 0.nonNeg
            }
        }
    }

    fun getUsersSharedEquipments(
        gameSessionId: GameSessionId,
        player1: PlayerId,
        player2: PlayerId
    ): Option<Pair<PlayerEquipment, PlayerEquipment>> = option {
        val playersData: Map<PlayerId, Pair<NonNegInt, NonNegInt>> = GameUserTable.select {
            (GameUserTable.gameSessionId eq gameSessionId) and (
                GameUserTable.playerId.inList(
                    listOf(
                        player1,
                        player2
                    )
                )
                )
        }.associate {
            it[GameUserTable.playerId] to (it[GameUserTable.sharedMoney] to it[GameUserTable.sharedTime])
        }
        val (money1, time1) = playersData[player1].toOption().bind()
        val (money2, time2) = playersData[player2].toOption().bind()

        val resources = PlayerResourceTable.select {
            PlayerResourceTable.gameSessionId eq gameSessionId and PlayerResourceTable.playerId.inList(
                listOf(player1, player2)
            )
        }.groupBy { it[PlayerResourceTable.playerId] }.mapValues { entry ->
            entry.value.associate { PlayerResourceTable.toSharedDomain(it) }
        }

        val player1Resources = resources[player1].toOption().flatMap { NonEmptyMap.fromMapSafe(it) }.bind()
        val player2Resources = resources[player2].toOption().flatMap { NonEmptyMap.fromMapSafe(it) }.bind()

        PlayerEquipment(money1, time1, player1Resources) to PlayerEquipment(
            money2,
            time2,
            player2Resources
        )
    }

    fun getPlayerWorkshopData(
        gameSessionId: GameSessionId,
        playerId: PlayerId
    ): Option<Triple<GameResourceName, PosInt, PosInt>> = GameUserTable.join(
        GameSessionUserClassesTable,
        JoinType.INNER
    ) {
        (GameUserTable.gameSessionId eq GameSessionUserClassesTable.gameSessionId) and (GameUserTable.className eq GameSessionUserClassesTable.className)
    }.select {
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
        val addition = PlayerEquipment(
            money = 0.nonNeg,
            time = 0.nonNeg,
            resources = NonEmptyMap.one(resourceName to quantity.toNonNeg())
        )
        val deletions = PlayerEquipment(
            money = (quantity * unitPrice).toNonNeg(),
            time = timeNeeded,
            resources = NonEmptyMap.one(resourceName to 0.nonNeg)
        )
        updateResources(
            gameSessionId,
            playerId,
            addition,
            deletions
        )()
    }

    fun getCityCosts(travelId: TravelId): NonEmptyMap<GameResourceName, NonNegInt> =
        NonEmptyMap.fromMapUnsafe(
            TravelResourcesTable.select {
                (TravelResourcesTable.travelId eq travelId)
            }.associate { TravelResourcesTable.toDomain(it) }
        )

    fun getTravelData(gameSessionId: GameSessionId, travelName: TravelName) = TravelsTable.select {
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
        additions = PlayerEquipment(
            money = reward.toNonNeg(),
            time = 0.nonNeg,
            resources = NonEmptyMap.fromMapUnsafe(cityCosts.map.mapValues { 0.nonNeg })
        ),
        deletions = PlayerEquipment(money = 0.nonNeg, time = time?.toNonNeg() ?: 0.nonNeg, resources = cityCosts)
    )

    fun getPlayerResourceValues(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        gameResourceName: GameResourceName
    ): Option<Pair<NonNegInt, NonNegInt>> = when (gameResourceName.value) {
        "time" -> GameUserTable.select { (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId) }
            .map { it[GameUserTable.time] to it[GameUserTable.sharedTime] }.firstOrNone()

        "money" -> GameUserTable.select { (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId) }
            .map { it[GameUserTable.money] to it[GameUserTable.sharedMoney] }.firstOrNone()

        else -> PlayerResourceTable.select {
            (PlayerResourceTable.gameSessionId eq gameSessionId) and (PlayerResourceTable.playerId eq playerId) and (PlayerResourceTable.resourceName eq gameResourceName)
        }.map { PlayerResourceTable.toValuePair(it) }.firstOrNone()
    }

    fun changeSharedResource(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        gameResourceName: GameResourceName,
        increase: Boolean
    ) = when (gameResourceName.value) {
        "time" -> GameUserTable.update({ (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId) }) {
            it.update(
                GameUserTable.sharedTime,
                if (increase) GameUserTable.sharedTime.plus(1) else GameUserTable.sharedTime.minus(1)
            )
        }

        "money" -> GameUserTable.update({ (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId) }) {
            it.update(
                GameUserTable.sharedMoney,
                if (increase) GameUserTable.sharedMoney.plus(1) else GameUserTable.sharedMoney.minus(1)
            )
        }

        else -> PlayerResourceTable.update({
            (PlayerResourceTable.gameSessionId eq gameSessionId) and (PlayerResourceTable.playerId eq playerId) and (PlayerResourceTable.resourceName eq gameResourceName)
        }) {
            it.update(
                PlayerResourceTable.sharedValue,
                if (increase) PlayerResourceTable.sharedValue.plus(1) else PlayerResourceTable.sharedValue.minus(1)
            )
        }
    }
}
