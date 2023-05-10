package pl.edu.agh.game.dao

import arrow.core.Option
import arrow.core.firstOrNone
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.domain.*
import pl.edu.agh.game.domain.GameUserDto
import pl.edu.agh.game.table.GameSessionTable
import pl.edu.agh.game.table.GameSessionUserClassesTable
import pl.edu.agh.game.table.GameUserTable

object GameUserDao {
    fun getAllUsersInGame(gameSessionId: GameSessionId): List<GameUserDto> =
        GameUserTable
            .select(GameUserTable.gameSessionId eq gameSessionId)
            .map { GameUserTable.toDomain(it) }

    fun getGameUserInfo(
        loginUserId: LoginUserId,
        gameSessionId: GameSessionId
    ): Option<PlayerStatus> =
        GameUserTable
            .join(GameSessionUserClassesTable, JoinType.INNER) {
                (GameUserTable.gameSessionId eq GameSessionUserClassesTable.gameSessionId) and
                    (GameUserTable.className eq GameSessionUserClassesTable.name)
            }
            .join(GameSessionTable, JoinType.INNER) {
                GameUserTable.gameSessionId eq GameSessionTable.id
            }
            .select {
                (GameUserTable.loginUserId eq loginUserId) and (GameUserTable.gameSessionId eq gameSessionId)
            }
            .firstOrNone()
            .map {
                PlayerStatus(
                    Coordinates(
                        it[GameSessionTable.startingX],
                        it[GameSessionTable.startingY]
                    ),
                    Direction.valueOf(it[GameSessionTable.startingDirection]),
                    it[GameUserTable.className],
                    it[GameUserTable.playerId]
                )
            }

    fun insertUser(
        loginUserId: LoginUserId,
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        randomClass: GameClassName
    ) {
        GameSessionTable.select {
            GameSessionTable.id eq gameSessionId
        }.map { it[GameSessionTable.defaultTimeValue] to it[GameSessionTable.defaultMoneyValue] }
            .firstOrNone().map { (defaultTime, defaultMoney) ->
                GameUserTable.insert {
                    it[GameUserTable.loginUserId] = loginUserId
                    it[GameUserTable.gameSessionId] = gameSessionId
                    it[GameUserTable.playerId] = playerId
                    it[GameUserTable.className] = randomClass
                    it[GameUserTable.money] = defaultMoney
                    it[GameUserTable.time] = defaultTime
                }
            }
    }

    fun getClassUsages(gameSessionId: GameSessionId): Map<GameClassName, Long> =
        GameUserTable
            .join(GameSessionUserClassesTable, JoinType.RIGHT) {
                (GameUserTable.gameSessionId eq GameSessionUserClassesTable.gameSessionId) and
                    (GameUserTable.className eq GameSessionUserClassesTable.name)
            }.slice(
                GameSessionUserClassesTable.name,
                GameUserTable.loginUserId.count()
            ).select {
                GameSessionUserClassesTable.gameSessionId eq gameSessionId
            }.groupBy(GameSessionUserClassesTable.name)
            .associate { it[GameSessionUserClassesTable.name] to it[GameUserTable.loginUserId.count()] }
}
