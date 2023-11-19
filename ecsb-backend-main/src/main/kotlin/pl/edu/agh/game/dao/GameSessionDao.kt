package pl.edu.agh.game.dao

import arrow.core.*
import org.jetbrains.exposed.sql.*
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.game.domain.GameSessionDto
import pl.edu.agh.game.domain.GameStatus
import pl.edu.agh.assets.domain.GameAssets
import pl.edu.agh.game.table.GameSessionTable
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.LiteralOpOwn
import java.time.Instant

object GameSessionDao {
    fun createGameSession(
        gameName: String,
        gameAssets: GameAssets,
        loginUserId: LoginUserId,
        timeForGame: TimestampMillis,
        interactionRadius: PosInt,
        maxTimeAmount: NonNegInt,
        walkingSpeed: PosInt,
        defaultMoneyValue: Money,
        maxPlayerAmount: NonNegInt
    ): GameSessionId =
        GameSessionTable.insert {
            it[GameSessionTable.name] = gameName
            it[GameSessionTable.createdBy] = loginUserId
            it[GameSessionTable.mapId] = gameAssets.mapAssetId
            it[GameSessionTable.character_spreadsheet_id] = gameAssets.characterAssetsId
            it[GameSessionTable.tiles_spreadsheet_id] = gameAssets.tileAssetsId
            it[GameSessionTable.resource_asset_id] = gameAssets.resourceAssetsId
            it[GameSessionTable.timeForGame] = timeForGame
            it[GameSessionTable.walkingSpeed] = walkingSpeed
            it[GameSessionTable.interactionRadius] = interactionRadius
            it[GameSessionTable.maxTimeAmount] = maxTimeAmount
            it[GameSessionTable.defaultMoneyValue] = defaultMoneyValue
            it[GameSessionTable.maxPlayerAmount] = maxPlayerAmount
        }[GameSessionTable.id]

    fun getGameSessionRadius(gameSessionId: GameSessionId): Option<PosInt> =
        GameSessionTable.slice(GameSessionTable.interactionRadius).select {
            GameSessionTable.id eq gameSessionId
        }.firstOrNone().map { it[GameSessionTable.interactionRadius] }

    fun getGameSession(gameSessionId: GameSessionId): Option<GameSessionDto> =
        GameSessionTable.select {
            GameSessionTable.id eq gameSessionId
        }.toDomain(GameSessionTable).firstOrNone()

    fun findGameSession(gameCode: String): Option<GameSessionId> =
        GameSessionTable.slice(GameSessionTable.id).select {
            GameSessionTable.shortName eq gameCode
        }.firstOrNone().map { it[GameSessionTable.id] }

    fun startGame(gameSessionId: GameSessionId): DB<Option<Unit>> = {
        val resultRows =
            GameSessionTable.update({ (GameSessionTable.id eq gameSessionId) and (GameSessionTable.startedAt.isNull()) }) {
                it[GameSessionTable.startedAt] = Instant.now()
            }

        if (resultRows > 0) {
            Unit.some()
        } else {
            none()
        }
    }

    fun getGameSessionNameAfterEnd(gameSessionId: GameSessionId): Option<String> =
        GameSessionTable.select {
            (GameSessionTable.id eq gameSessionId)
        }.firstOrNone().flatMap {
            if (it[GameSessionTable.endedAt] is Instant) {
                it[GameSessionTable.name].some()
            } else {
                none()
            }
        }

    fun getGameSessionLeftTime(gameSessionId: GameSessionId): Option<TimestampMillis> =
        GameSessionTable.select { GameSessionTable.id eq gameSessionId }.firstOrNone()
            .flatMap { GameSessionTable.getTimeLeft(it) }

    fun getGameSessionTimes(): Option<NonEmptyMap<GameSessionId, TimestampMillis>> =
        GameSessionTable.select { GameSessionTable.endedAt.isNull() }
            .associate { it[GameSessionTable.id] to GameSessionTable.getTimeLeft(it) }
            .filterOption()
            .toNonEmptyMapOrNone()

    fun endGameSessions(): List<GameSessionId> = GameSessionTable.updateReturning(
        where = {
            PlusOp(
                GameSessionTable.startedAt,
                MillisToInstant(GameSessionTable.timeForGame),
                GameSessionTable.startedAt.columnType
            ).less(Instant.now()) and GameSessionTable.endedAt.isNull()
        },
        from = GameSessionTable.alias("old_game_session"),
        joinColumns = listOf(GameSessionTable.id),
        updateObjects = UpdateObject(
            GameSessionTable.endedAt,
            LiteralOp(JavaTimestampWithTimeZoneColumnType(), Instant.now())
        ),
        returningNew = mapOf("gameSessionId" to GameSessionTable.id)
    ).map { (it.returningNew["gameSessionId"] as GameSessionId?).toOption() }.flattenOption()

    fun getGameSessionsEndedTimeAgo(time: TimestampMillis): List<GameSessionId> =
        GameSessionTable.slice(GameSessionTable.id).select {
            ((GameSessionTable.endedAt + MillisToInstant(time)) greater Instant.now()) and (GameSessionTable.endedAt.isNotNull())
        }.map { it[GameSessionTable.id] }

    fun getUsersMaxAmount(gameSessionId: GameSessionId): Option<NonNegInt> =
        GameSessionTable.slice(GameSessionTable.maxPlayerAmount).select { GameSessionTable.id eq gameSessionId }
            .map { resultRow ->
                resultRow[GameSessionTable.maxPlayerAmount]
            }.firstOrNone()

    fun getGameStatus(gameSessionId: GameSessionId): Option<GameStatus> =
        GameSessionTable.slice(GameSessionTable.startedAt, GameSessionTable.endedAt)
            .select { GameSessionTable.id eq gameSessionId }
            .map { resultRow ->
                if (resultRow[GameSessionTable.startedAt] == null) {
                    GameStatus.NOT_STARTED
                } else if (resultRow[GameSessionTable.endedAt] == null) {
                    GameStatus.STARTED
                } else {
                    GameStatus.ENDED
                }
            }.firstOrNone()
}

class MillisToInstant(private val expr: Expression<TimestampMillis>) :
    ExpressionWithColumnType<Instant>() {
    override val columnType: IColumnType = JavaTimestampWithTimeZoneColumnType()

    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        return queryBuilder {
            append("(", expr, " * INTERVAL '1 millisecond')")
        }
    }

    companion object {
        operator fun invoke(literal: TimestampMillis): MillisToInstant =
            MillisToInstant(LiteralOpOwn(TimestampMillis.columnType, literal))
    }
}
