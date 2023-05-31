package pl.edu.agh.game.dao

import arrow.core.Option
import arrow.core.firstOrNone
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.assets.table.MapAssetDataTable
import pl.edu.agh.assets.table.MapAssetTable
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.domain.*
import pl.edu.agh.game.domain.GameUserDto
import pl.edu.agh.game.table.GameSessionTable
import pl.edu.agh.game.table.GameSessionUserClassesTable
import pl.edu.agh.game.table.GameUserTable

object GameUserDao {

    fun getUserInGame(gameSessionId: GameSessionId, loginUserId: LoginUserId): Option<GameUserDto> =
        GameUserTable
            .select((GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.loginUserId eq loginUserId) and (GameUserTable.inGame))
            .firstOrNone().map { GameUserTable.toDomain(it) }

    fun getAllUsersInGame(gameSessionId: GameSessionId): List<GameUserDto> =
        GameUserTable
            .select((GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.inGame))
            .map { GameUserTable.toDomain(it) }

    fun getGameUserInfo(
        loginUserId: LoginUserId,
        gameSessionId: GameSessionId
    ): Option<PlayerStatus> =
        GameUserTable
            .join(GameSessionTable, JoinType.INNER) {
                GameUserTable.gameSessionId eq GameSessionTable.id
            }
            .join(MapAssetDataTable, JoinType.INNER) {
                MapAssetDataTable.id eq GameSessionTable.mapId and MapAssetDataTable.getData(MapDataTypes.StartingPoint)
            }
            .join(MapAssetTable, JoinType.INNER) {
                MapAssetTable.id eq GameSessionTable.mapId
            }
            .select {
                (GameUserTable.loginUserId eq loginUserId) and (GameUserTable.gameSessionId eq gameSessionId)
            }
            .firstOrNone()
            .map {
                PlayerStatus(
                    Coordinates(
                        it[MapAssetDataTable.x],
                        it[MapAssetDataTable.y]
                    ),
                    Direction.DOWN,
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
                        (GameUserTable.className eq GameSessionUserClassesTable.className)
            }.slice(
                GameSessionUserClassesTable.className,
                GameUserTable.loginUserId.count()
            ).select {
                GameSessionUserClassesTable.gameSessionId eq gameSessionId
            }.groupBy(GameSessionUserClassesTable.className)
            .associate { it[GameSessionUserClassesTable.className] to it[GameUserTable.loginUserId.count()] }

    fun updateUserInGame(gameSessionId: GameSessionId, userId: LoginUserId, inGame: Boolean) =
        GameUserTable
            .update(
                where = {
                    (GameUserTable.loginUserId eq userId) and (GameUserTable.gameSessionId eq gameSessionId)
                },
                body = {
                    it[GameUserTable.inGame] = inGame
                }
            )
}
