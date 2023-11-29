package pl.edu.agh.game.dao

import arrow.core.Option
import arrow.core.firstOrNone
import arrow.core.getOrElse
import arrow.core.raise.option
import arrow.core.toOption
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import pl.edu.agh.assets.domain.MapDataTypes
import pl.edu.agh.assets.table.MapAssetDataTable
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.domain.*
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.moving.domain.Coordinates
import pl.edu.agh.moving.domain.Direction
import pl.edu.agh.game.domain.GameUserDto
import pl.edu.agh.game.domain.PlayerResult
import pl.edu.agh.game.table.GameSessionTable
import pl.edu.agh.game.table.GameSessionUserClassesTable
import pl.edu.agh.game.table.GameUserTable
import pl.edu.agh.game.table.PlayerResourceTable
import pl.edu.agh.moving.domain.PlayerStatus
import pl.edu.agh.time.table.PlayerTimeTokenTable
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.PosInt.Companion.pos
import java.time.Instant

object GameUserDao {

    fun getUserBusyStatus(gameSessionId: GameSessionId, playerId: PlayerId): DB<Option<InteractionStatus>> = {
        GameUserTable.slice(GameUserTable.busyStatus)
            .select((GameUserTable.playerId eq playerId) and (GameUserTable.gameSessionId eq gameSessionId))
            .firstOrNone()
            .map { it[GameUserTable.busyStatus] }
    }

    fun setUserNotBusy(gameSessionId: GameSessionId, playerId: PlayerId): DB<Unit> = {
        GameUserTable.update(where = {
            (GameUserTable.playerId eq playerId) and (GameUserTable.gameSessionId eq gameSessionId)
        }, body = {
            it[GameUserTable.busyStatus] = InteractionStatus.NOT_BUSY
        })
    }

    fun setUserBusyStatus(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        interactionStatus: InteractionStatus
    ): DB<Boolean> = setUserBusyStatuses(gameSessionId, nonEmptyMapOf(playerId to interactionStatus))

    fun setUserBusyStatuses(
        gameSessionId: GameSessionId,
        playerStatuses: NonEmptyMap<PlayerId, InteractionStatus>
    ): DB<Boolean> = {
        val playerCases = playerStatuses.toList().map { (playerId, interactionStatus) ->
            val checkedInteractionStatus = if (interactionStatus == InteractionStatus.NOT_BUSY) {
                interactionStatus.literal()
            } else {
                Case(null)
                    .When(GameUserTable.busyStatus eq InteractionStatus.NOT_BUSY, interactionStatus.literal())
                    .When(GameUserTable.busyStatus eq interactionStatus, interactionStatus.literal())
                    .Else(GameUserTable.busyStatus)
            }
            GameUserTable.playerId eq playerId to checkedInteractionStatus
        }.fold(CaseWhen<InteractionStatus>(null)) { initial, (playerCheck, checkedInteractionStatus) ->
            initial.When(playerCheck, checkedInteractionStatus)
        }.Else(GameUserTable.busyStatus)

        val oldGameUser = GameUserTable.alias("old_game_user")
        GameUserTable.updateReturning(
            where = {
                (GameUserTable.playerId.inList(playerStatuses.keys)) and (GameUserTable.gameSessionId eq gameSessionId)
            },
            from = oldGameUser,
            joinColumns = listOf(GameUserTable.gameSessionId, GameUserTable.playerId),
            updateObjects = UpdateObject(GameUserTable.busyStatus, playerCases),
            returningNew = mapOf<String, Column<PlayerId>>("playerId" to GameUserTable.playerId)
        ).map {
            option {
                val (oldValue, newValue) = it.returningBoth[GameUserTable.busyStatus.name].toOption().bind()
                val playerId = it.returningNew["playerId"].toOption().bind()
                val expectedStatus = playerStatuses[playerId].toOption().bind()
                ensure(oldValue == InteractionStatus.NOT_BUSY || oldValue == expectedStatus)
                ensure(newValue == expectedStatus)
                true
            }.getOrElse { false }
        }.fold(true) { initial, next ->
            initial && next
        }
    }

    fun getUserInGame(gameSessionId: GameSessionId, loginUserId: LoginUserId): Option<GameUserDto> =
        GameUserTable
            .select((GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.loginUserId eq loginUserId) and (GameUserTable.inGame))
            .toDomain(GameUserTable)
            .firstOrNone()

    fun getUserInGame(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        exceptLoginUserId: LoginUserId
    ): Option<GameUserDto> =
        GameUserTable
            .select((GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.playerId eq playerId) and (GameUserTable.loginUserId neq exceptLoginUserId))
            .toDomain(GameUserTable)
            .firstOrNone()

    fun getAllUsersInGame(gameSessionId: GameSessionId): List<GameUserDto> =
        GameUserTable
            .select((GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.inGame eq true))
            .toDomain(GameUserTable)

    fun getUsersResults(gameSessionId: GameSessionId): List<PlayerResult> {
        val totalMoneyQuery =
            GameUserTable.money.castTo<Money?>(LongColumnType())
                .plus(
                    PlayerResourceTable.value.times2<NonNegInt, Long, Money>(GameSessionUserClassesTable.buyoutPrice)
                        .sum()
                )
                .castTo<Long>(LongColumnType())
                .alias("totalMoney")
        val query = GameUserTable
            .join(PlayerResourceTable, JoinType.INNER) {
                (PlayerResourceTable.playerId eq GameUserTable.playerId) and (PlayerResourceTable.gameSessionId eq GameUserTable.gameSessionId)
            }
            .join(GameSessionUserClassesTable, JoinType.INNER) {
                (GameSessionUserClassesTable.gameSessionId eq GameUserTable.gameSessionId) and (GameSessionUserClassesTable.resourceName eq PlayerResourceTable.resourceName)
            }
            .join(GameSessionTable, JoinType.INNER) {
                (GameSessionUserClassesTable.gameSessionId eq GameSessionTable.id)
            }
            .slice(GameUserTable.playerId, totalMoneyQuery)
            .select {
                (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.createdAt.lessEq(GameSessionTable.endedAt))
            }
            .groupBy(GameUserTable.playerId, GameUserTable.money)
            .orderBy(totalMoneyQuery, SortOrder.DESC)

        return query
            .map { PlayerResult(it[GameUserTable.playerId], it[totalMoneyQuery].toOption().getOrElse { 0 }) }
    }

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
            .slice(
                MapAssetDataTable.x,
                MapAssetDataTable.y,
                GameUserTable.className,
                GameUserTable.playerId
            )
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
        }.map { it[GameSessionTable.maxTimeAmount] to it[GameSessionTable.defaultMoneyValue] }
            .firstOrNone().map { (defaultTime, defaultMoney) ->
                GameUserTable.insert {
                    it[GameUserTable.loginUserId] = loginUserId
                    it[GameUserTable.gameSessionId] = gameSessionId
                    it[GameUserTable.playerId] = playerId
                    it[GameUserTable.className] = randomClass
                    it[GameUserTable.money] = defaultMoney
                    it[GameUserTable.busyStatus] = InteractionStatus.NOT_BUSY
                    it[GameUserTable.inGame] = false
                }

                PlayerTimeTokenTable.insert {
                    it[PlayerTimeTokenTable.gameSessionId] = gameSessionId
                    it[PlayerTimeTokenTable.playerId] = playerId
                    it[PlayerTimeTokenTable.actualState] = (MAX_TIME_TOKEN_STATE.value * (defaultTime.value)).nonNeg
                    it[PlayerTimeTokenTable.maxState] = (MAX_TIME_TOKEN_STATE.value * (defaultTime.value)).pos
                    it[PlayerTimeTokenTable.alterDate] = Instant.now()
                }
            }
    }

    fun getClassUsages(gameSessionId: GameSessionId): Map<GameClassName, Long> =
        GameUserTable
            .join(GameSessionUserClassesTable, JoinType.RIGHT) {
                (GameUserTable.gameSessionId eq GameSessionUserClassesTable.gameSessionId) and (GameUserTable.className eq GameSessionUserClassesTable.className)
            }.slice(
                GameSessionUserClassesTable.className,
                GameUserTable.loginUserId.count()
            ).select {
                GameSessionUserClassesTable.gameSessionId eq gameSessionId
            }.groupBy(GameSessionUserClassesTable.className)
            .associate { it[GameSessionUserClassesTable.className] to it[GameUserTable.loginUserId.count()] }

    fun updateUserInGame(gameSessionId: GameSessionId, playerId: PlayerId, inGame: Boolean) =
        GameUserTable
            .update(
                where = {
                    (GameUserTable.playerId eq playerId) and (GameUserTable.gameSessionId eq gameSessionId) and (GameUserTable.inGame eq !inGame)
                },
                body = {
                    it[GameUserTable.inGame] = inGame
                }
            )

    val MAX_TIME_TOKEN_STATE = 50.pos
}

private fun <T, R, R1> Column<T>.times2(buyoutPrice: Column<R>): ExpressionWithColumnType<R1> {
    return CustomOperator("*", columnType, this, buyoutPrice)
}
