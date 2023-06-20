package pl.edu.agh.game.dao

import arrow.core.*
import arrow.core.raise.option
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.table.GameSessionUserClassesTable
import pl.edu.agh.game.table.GameUserTable
import pl.edu.agh.game.table.PlayerResourceTable
import pl.edu.agh.travel.domain.TravelId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.table.TravelResourcesTable
import pl.edu.agh.travel.table.TravelsTable
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt
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
                iorChange.fold({ left ->
                    it.update(PlayerResourceTable.value, PlayerResourceTable.value + left)
                }, { right ->
                    it.update(PlayerResourceTable.value, PlayerResourceTable.value - right)
                }, { left, right ->
                    it.update(PlayerResourceTable.value, PlayerResourceTable.value + left - right)
                })
            }
        }

        val changes = additions.resources.padZip(deletions.resources).forEach { resourceName, (addition, deletion) ->
            Ior.fromNullables(addition, deletion)?.let {
                updateResourceValue(resourceName, it)
            }
        }

        GameUserTable.update({ (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId) }) {
            with(SqlExpressionBuilder) {
                it.update(GameUserTable.money, GameUserTable.money + additions.money - deletions.money)
                it.update(GameUserTable.time, GameUserTable.time + additions.time - deletions.time)
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

    fun getUserEquipmentByLoginUserId(gameSessionId: GameSessionId, loginUserId: LoginUserId): Option<PlayerEquipment> =
        option {
            val (time, money, playerId) = GameUserTable.select {
                (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.loginUserId eq loginUserId)
            }.map { Triple(it[GameUserTable.time], it[GameUserTable.money], it[GameUserTable.playerId]) }
                .firstOrNone().bind()
            val resources = getPlayerResources(gameSessionId, playerId).bind()

            PlayerEquipment(money, time, resources)
        }

    fun getUserEquipmentByPlayerId(gameSessionId: GameSessionId, playerId: PlayerId): Option<PlayerEquipment> =
        option {
            val (time, money) = GameUserTable.select {
                (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId)
            }.map { it[GameUserTable.time] to it[GameUserTable.money] }
                .firstOrNone().bind()
            val resources = getPlayerResources(gameSessionId, playerId).bind()

            PlayerEquipment(money, time, resources)
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
                    it[PlayerResourceTable.value] = NonNegInt(0)
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
                entry.value.associate { PlayerResourceTable.toDomain(it) }
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
}
