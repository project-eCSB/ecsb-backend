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
import pl.edu.agh.game.domain.GameResourceDto
import pl.edu.agh.game.table.GameSessionUserClassesTable
import pl.edu.agh.game.table.GameUserTable
import pl.edu.agh.game.table.PlayerResourceTable
import pl.edu.agh.travel.domain.TravelId
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.table.TravelResourcesTable
import pl.edu.agh.travel.table.TravelsTable

object PlayerResourceDao {
    fun updateResources(gameSessionId: GameSessionId, playerId: PlayerId, equipmentChanges: PlayerEquipment) {
        equipmentChanges.resources.forEach { (resourceName, resourceValue) ->
            PlayerResourceTable.update({
                (PlayerResourceTable.gameSessionId eq gameSessionId) and
                    (PlayerResourceTable.playerId eq playerId) and
                    (PlayerResourceTable.resourceName eq resourceName)
            }) {
                it.update(PlayerResourceTable.value, PlayerResourceTable.value + resourceValue)
            }
        }

        GameUserTable.update({ (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId) }) {
            with(SqlExpressionBuilder) {
                it.update(GameUserTable.money, GameUserTable.money + equipmentChanges.money)
                it.update(GameUserTable.time, GameUserTable.time + equipmentChanges.time)
            }
        }
    }

    fun getPlayerResources(gameSessionId: GameSessionId, playerId: PlayerId) = PlayerResourceTable.select {
        (PlayerResourceTable.gameSessionId eq gameSessionId) and (PlayerResourceTable.playerId eq playerId)
    }.map { PlayerResourceTable.toDomain(it) }

    fun getUserEquipmentByLoginUserId(gameSessionId: GameSessionId, loginUserId: LoginUserId): Option<PlayerEquipment> =
        option {
            val (time, money, playerId) = GameUserTable.select {
                (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.loginUserId eq loginUserId)
            }.map { Triple(it[GameUserTable.time], it[GameUserTable.money], it[GameUserTable.playerId]) }
                .firstOrNone().bind()
            val resources = getPlayerResources(gameSessionId, playerId)

            PlayerEquipment(money, time, resources)
        }

    fun getUserEquipmentByPlayerId(gameSessionId: GameSessionId, playerId: PlayerId): Option<PlayerEquipment> =
        option {
            val (time, money) = GameUserTable.select {
                (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId)
            }.map { it[GameUserTable.time] to it[GameUserTable.money] }
                .firstOrNone().bind()
            val resources = getPlayerResources(gameSessionId, playerId)

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
                    it[PlayerResourceTable.value] = 0
                }
            }
    }

    fun getUsersEquipments(
        gameSessionId: GameSessionId,
        player1: PlayerId,
        player2: PlayerId
    ): Option<Pair<PlayerEquipment, PlayerEquipment>> =
        option {
            val playersData: Map<PlayerId, Pair<Int, Int>> = GameUserTable.select {
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
                entry.value.map { PlayerResourceTable.toDomain(it) }
            }

            val player1Resources = resources[player1].toOption().bind()
            val player2Resources = resources[player2].toOption().bind()

            PlayerEquipment(money1, time1, player1Resources) to PlayerEquipment(money2, time2, player2Resources)
        }

    fun getPlayerData(
        gameSessionId: GameSessionId,
        playerId: PlayerId
    ): Option<Tuple4<GameResourceName, Int, Int, Int>> =
        GameUserTable.join(
            GameSessionUserClassesTable,
            JoinType.INNER
        ) {
            (GameUserTable.gameSessionId eq GameSessionUserClassesTable.gameSessionId) and (GameUserTable.className eq GameSessionUserClassesTable.className)
        }.select {
            (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId)
        }.map {
            Tuple4(
                it[GameSessionUserClassesTable.resourceName],
                it[GameUserTable.money],
                it[GameSessionUserClassesTable.unitPrice],
                it[GameSessionUserClassesTable.maxProduction]
            )
        }.firstOrNone()

    fun getPlayerMoneyAndTime(gameSessionId: GameSessionId, playerId: PlayerId) = GameUserTable.select {
        (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId)
    }.map { it[GameUserTable.money] to it[GameUserTable.time] }.firstOrNone()

    fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        resourceName: GameResourceName,
        quantity: Int,
        unitPrice: Int
    ) {
        PlayerResourceTable.update({
            (PlayerResourceTable.gameSessionId eq gameSessionId) and
                (PlayerResourceTable.playerId eq playerId) and
                (PlayerResourceTable.resourceName eq resourceName)
        }) {
            it.update(PlayerResourceTable.value, PlayerResourceTable.value + quantity)
        }

        GameUserTable.update({ (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId) }) {
            with(SqlExpressionBuilder) {
                it.update(GameUserTable.money, GameUserTable.money - quantity * unitPrice)
            }
        }
    }

    fun getCityCosts(travelId: TravelId) = TravelResourcesTable.select {
        (TravelResourcesTable.travelId eq travelId)
    }.map { TravelResourcesTable.toDomain(it) }

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
        cityCosts: List<GameResourceDto>,
        reward: Int,
        time: Int?
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
                it.update(GameUserTable.money, GameUserTable.money + reward)
                if (time != null) {
                    it.update(GameUserTable.time, GameUserTable.time - time)
                }
            }
        }
    }
}
