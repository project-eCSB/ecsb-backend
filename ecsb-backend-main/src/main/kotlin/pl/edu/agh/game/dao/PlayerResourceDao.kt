package pl.edu.agh.game.dao

import arrow.core.*
import arrow.core.raise.either
import arrow.core.raise.option
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
import pl.edu.agh.utils.DB
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.NonNegInt.Companion.minus
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.NonNegInt.Companion.plus
import pl.edu.agh.utils.PosInt

object PlayerResourceDao {
    fun updateResources(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        additions: PlayerEquipment,
        deletions: PlayerEquipment
    ): DB<Either<Unit, Unit>> = {
        val allResourcesToBeUpdated = additions.resources
            .padZip(deletions.resources)
            .map { (resourceName, change) ->
                val (addition, deletion) = change
                Ior.fromNullables(addition, deletion).toOption().map {
                    resourceName to it
                }
            }
            .flattenOption()

        val resourcesValuesClause =
            allResourcesToBeUpdated
                .fold(CaseWhen<NonNegInt>(null)) { initialCase, (resourceName, iorChange) ->
                    val updatedValue =
                        iorChange.fold(
                            { left -> PlayerResourceTable.value + left },
                            { right -> PlayerResourceTable.value - right },
                            { left, right -> PlayerResourceTable.value + left - right }
                        )

                    initialCase.When(
                        PlayerResourceTable.resourceName eq resourceName,
                        PlayerResourceTable.value + updatedValue
                    )
                }.let {
                    CaseWhenElse(it, PlayerResourceTable.value.minus(10000000))
                }

        either {
            val updatedRows = PlayerResourceTable.update({
                (PlayerResourceTable.gameSessionId eq gameSessionId) and
                    (PlayerResourceTable.playerId eq playerId) and (resourcesValuesClause.greaterEq(0.nonNeg))
            }) {
                it.update(PlayerResourceTable.value, resourcesValuesClause)
                it.update(
                    PlayerResourceTable.sharedValue,
                    case()
                        .When(resourcesValuesClause.less(PlayerResourceTable.sharedValue), resourcesValuesClause)
                        .Else(PlayerResourceTable.sharedValue)
                )
            }

            if (updatedRows < allResourcesToBeUpdated.size) {
                raise(Unit)
            }

            val updatedMoney = GameUserTable.money + additions.money - deletions.money
            val updatedTime = GameUserTable.time + additions.time - deletions.time

            val updatedGameUserRows = GameUserTable.update({
                (GameUserTable.gameSessionId eq gameSessionId) and
                    (GameUserTable.playerId eq playerId) and
                    (updatedMoney.greaterEq(0.nonNeg)) and (updatedTime.greaterEq(0.nonNeg))
            }) {
                with(SqlExpressionBuilder) {
                    it.update(GameUserTable.money, updatedMoney)
                    it.update(
                        GameUserTable.sharedMoney,
                        case(null).When(LessOp(updatedMoney, GameUserTable.sharedMoney), updatedMoney)
                            .Else(GameUserTable.sharedMoney)
                    )
                    it.update(GameUserTable.time, updatedTime)
                    it.update(
                        GameUserTable.sharedTime,
                        case(null).When(LessOp(updatedTime, GameUserTable.sharedTime), updatedTime)
                            .Else(GameUserTable.sharedTime)
                    )
                }
            }

            if (updatedGameUserRows == 0) {
                raise(Unit)
            }
        }.onLeft { rollback() }
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
    ): Option<PlayerEquipmentView> =
        option {
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
        }.map { it[GameSessionUserClassesTable.resourceName] }
            .forEach { gameResourceName ->
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
    ): Option<Pair<PlayerEquipment, PlayerEquipment>> =
        option {
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
    ): Option<Triple<GameResourceName, PosInt, PosInt>> =
        GameUserTable.join(
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
    ): DB<Either<Unit, Unit>> = {
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
    ): DB<Either<Unit, Unit>> =
        updateResources(
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
    ): Option<Pair<NonNegInt, NonNegInt>> =
        when (gameResourceName.value) {
            "time" -> GameUserTable.select { (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId) }
                .map { it[GameUserTable.time] to it[GameUserTable.sharedTime] }.firstOrNone()

            "money" -> GameUserTable.select { (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId) }
                .map { it[GameUserTable.money] to it[GameUserTable.sharedMoney] }.firstOrNone()

            else -> PlayerResourceTable.select {
                (PlayerResourceTable.gameSessionId eq gameSessionId) and (PlayerResourceTable.playerId eq playerId) and
                    (PlayerResourceTable.resourceName eq gameResourceName)
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
            (PlayerResourceTable.gameSessionId eq gameSessionId) and (PlayerResourceTable.playerId eq playerId) and
                (PlayerResourceTable.resourceName eq gameResourceName)
        }) {
            it.update(
                PlayerResourceTable.sharedValue,
                if (increase) PlayerResourceTable.sharedValue.plus(1) else PlayerResourceTable.sharedValue.minus(1)
            )
        }
    }
}
