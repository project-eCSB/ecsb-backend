package pl.edu.agh.game.dao

import arrow.core.*
import arrow.core.raise.option
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.case
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
    ) {
        fun updateResourceValue(resourceName: GameResourceName, iorChange: Ior<NonNegInt, NonNegInt>) {
            PlayerResourceTable.update({
                (PlayerResourceTable.gameSessionId eq gameSessionId) and
                        (PlayerResourceTable.playerId eq playerId) and
                        (PlayerResourceTable.resourceName eq resourceName)
            }) {
                val updatedValue =
                    iorChange.fold(
                        { left -> PlayerResourceTable.value + left },
                        { right -> PlayerResourceTable.value - right },
                        { left, right -> PlayerResourceTable.value + left - right }
                    )

                it.update(PlayerResourceTable.value, updatedValue)
                it.update(
                    PlayerResourceTable.sharedValue,
                    case()
                        .When(LessOp(updatedValue, PlayerResourceTable.sharedValue), updatedValue)
                        .Else(PlayerResourceTable.sharedValue)
                )
            }
        }

        additions.resources.padZip(deletions.resources).forEach { resourceName, (addition, deletion) ->
            Ior.fromNullables(addition, deletion)?.let {
                updateResourceValue(resourceName, it)
            }
        }

        GameUserTable.update({ (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId) }) {
            with(SqlExpressionBuilder) {
                val updatedMoney = GameUserTable.money + additions.money - deletions.money
                val updatedTime = GameUserTable.time + additions.time - deletions.time
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
    }

    fun getPlayerResources(
        gameSessionId: GameSessionId,
        playerId: PlayerId
    ): Option<NonEmptyMap<GameResourceName, NonNegInt>> =
        PlayerResourceTable.select {
            (PlayerResourceTable.gameSessionId eq gameSessionId) and (PlayerResourceTable.playerId eq playerId)
        }.associate { PlayerResourceTable.toDomain(it) }.toOption().flatMap { NonEmptyMap.fromMapSafe(it) }

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

        val playerResourcesOption = NonEmptyMap.fromMapSafe(playerResources)
        val sharedPlayerResourcesOption = NonEmptyMap.fromMapSafe(sharedPlayerResources)

        return playerResourcesOption.flatMap { playerRes ->
            sharedPlayerResourcesOption.map { sharedRes -> playerRes to sharedRes }
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

    fun getUsersEquipments(
        gameSessionId: GameSessionId,
        player1: PlayerId,
        player2: PlayerId
    ): Option<Pair<PlayerEquipment, PlayerEquipment>> =
        option {
            val playersData: Map<PlayerId, Pair<NonNegInt, NonNegInt>> = GameUserTable.select {
                GameUserTable.playerId.inList(listOf(player1, player2))
            }.associate {
                it[GameUserTable.playerId] to (it[GameUserTable.money] to it[GameUserTable.time])
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

    fun getPlayerData(
        gameSessionId: GameSessionId,
        playerId: PlayerId
    ): Option<Tuple5<GameResourceName, PosInt, PosInt, NonNegInt, NonNegInt>> =
        GameUserTable.join(
            GameSessionUserClassesTable,
            JoinType.INNER
        ) {
            (GameUserTable.gameSessionId eq GameSessionUserClassesTable.gameSessionId) and (GameUserTable.className eq GameSessionUserClassesTable.className)
        }.select {
            (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId)
        }.map {
            Tuple5(
                it[GameSessionUserClassesTable.resourceName],
                it[GameSessionUserClassesTable.unitPrice],
                it[GameSessionUserClassesTable.maxProduction],
                it[GameUserTable.money],
                it[GameUserTable.time]
            )
        }.firstOrNone()

    fun getPlayerMoneyAndTime(gameSessionId: GameSessionId, playerId: PlayerId) = GameUserTable.select {
        (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId)
    }.map { it[GameUserTable.money] to it[GameUserTable.time] }.firstOrNone()

    fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        resourceName: GameResourceName,
        quantity: PosInt,
        unitPrice: PosInt,
        timeNeeded: NonNegInt
    ) {
        PlayerResourceTable.update({
            (PlayerResourceTable.gameSessionId eq gameSessionId) and
                    (PlayerResourceTable.playerId eq playerId) and
                    (PlayerResourceTable.resourceName eq resourceName)
        }) {
            it.update(PlayerResourceTable.value, PlayerResourceTable.value + quantity.toNonNeg())
        }

        GameUserTable.update({ (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId) }) {
            with(SqlExpressionBuilder) {
                it.update(GameUserTable.money, GameUserTable.money - (quantity * unitPrice).toNonNeg())
                it.update(GameUserTable.time, GameUserTable.time - timeNeeded)
            }
        }
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
    ) {
        cityCosts.forEach { (resourceName, resourceValue) ->
            PlayerResourceTable.update({
                (PlayerResourceTable.gameSessionId eq gameSessionId) and
                        (PlayerResourceTable.playerId eq playerId) and
                        (PlayerResourceTable.resourceName eq resourceName)
            }) {
                it.update(PlayerResourceTable.value, PlayerResourceTable.value - resourceValue)
            }
        }

        GameUserTable.update({ (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId) }) {
            with(SqlExpressionBuilder) {
                it.update(GameUserTable.money, GameUserTable.money + reward.toNonNeg())
                if (time != null) {
                    it.update(GameUserTable.time, GameUserTable.time - time.toNonNeg())
                }
            }
        }
    }

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
