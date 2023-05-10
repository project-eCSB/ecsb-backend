package pl.edu.agh.game.dao

import arrow.core.Option
import arrow.core.firstOrNone
import arrow.core.raise.option
import arrow.core.toOption
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.table.GameSessionResourceTable
import pl.edu.agh.game.table.GameUserTable
import pl.edu.agh.game.table.PlayerResourceTable

object PlayerResourceDao {
    fun updateResources(gameSessionId: GameSessionId, playerId: PlayerId, equipmentChanges: PlayerEquipment) {
        equipmentChanges.products.forEach { (resourceId, resourceValue) ->
            PlayerResourceTable.update({
                (PlayerResourceTable.gameSessionId eq gameSessionId) and
                    (PlayerResourceTable.playerId eq playerId) and
                    (PlayerResourceTable.resourceId eq resourceId)
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

    fun getUserEquipmentByLoginUserId(gameSessionId: GameSessionId, loginUserId: LoginUserId): Option<PlayerEquipment> =
        option {
            val (time, money, playerId) = GameUserTable.select {
                (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.loginUserId eq loginUserId)
            }.map { Triple(it[GameUserTable.time], it[GameUserTable.money], it[GameUserTable.playerId]) }
                .firstOrNone().bind()
            val resources = PlayerResourceTable.select {
                (PlayerResourceTable.gameSessionId eq gameSessionId) and (PlayerResourceTable.playerId eq playerId)
            }.associate { it[PlayerResourceTable.resourceId] to it[PlayerResourceTable.value] }

            PlayerEquipment(money, time, resources)
        }

    fun getUserEquipmentByPlayerId(gameSessionId: GameSessionId, playerId: PlayerId): Option<PlayerEquipment> =
        option {
            val (time, money) = GameUserTable.select {
                (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId)
            }.map { it[GameUserTable.time] to it[GameUserTable.money] }
                .firstOrNone().bind()
            val resources = PlayerResourceTable.select {
                (PlayerResourceTable.gameSessionId eq gameSessionId) and (PlayerResourceTable.playerId eq playerId)
            }.associate { it[PlayerResourceTable.resourceId] to it[PlayerResourceTable.value] }

            PlayerEquipment(money, time, resources)
        }

    fun insertUserResources(gameSessionId: GameSessionId, playerId: PlayerId) {
        GameSessionResourceTable.select {
            GameSessionResourceTable.gameSessionId eq gameSessionId
        }.map { it[GameSessionResourceTable.id] }
            .forEach { resourceId ->
                PlayerResourceTable.insert {
                    it[PlayerResourceTable.gameSessionId] = gameSessionId
                    it[PlayerResourceTable.playerId] = playerId
                    it[PlayerResourceTable.resourceId] = resourceId
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
            }.groupBy { it[PlayerResourceTable.playerId] }.mapValues {
                it.value.associate { row -> row[PlayerResourceTable.resourceId] to row[PlayerResourceTable.value] }
            }

            val player1Resources = resources[player1].toOption().bind()
            val player2Resources = resources[player2].toOption().bind()

            PlayerEquipment(money1, time1, player1Resources) to PlayerEquipment(money2, time2, player2Resources)
        }
}
