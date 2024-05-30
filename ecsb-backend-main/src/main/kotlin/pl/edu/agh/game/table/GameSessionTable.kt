package pl.edu.agh.game.table

import arrow.core.Option
import arrow.core.toOption
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.assets.domain.FileType
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.domain.loginUserId
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.game.domain.GameSessionDto
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNegDbWrapper
import pl.edu.agh.utils.PosInt.Companion.posIntWrapper
import java.time.Instant

object GameSessionTable : Table("GAME_SESSION"), Domainable<GameSessionDto> {
    val id: Column<GameSessionId> = intWrapper(GameSessionId::value, ::GameSessionId)("ID").autoIncrement()
    val name: Column<String> = varchar("NAME", 255)
    val mapId: Column<SavedAssetsId> = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("MAP_ID")
    val shortName: Column<String> = varchar("SHORT_CODE", 255)
    val createdBy: Column<LoginUserId> = loginUserId("CREATED_BY")
    val defaultMoneyValue: Column<Money> = longWrapper(Money::value, ::Money)("DEFAULT_MONEY_VALUE")
    val maxTimeTokens: Column<NonNegInt> = nonNegDbWrapper("MAX_TIME_TOKENS")
    val timeForGame: Column<TimestampMillis> = longWrapper(TimestampMillis::value, ::TimestampMillis)("TIME_FOR_GAME")
    val startedAt: Column<Instant?> = timestampWithTimeZone("STARTED_AT").nullable()
    val endedAt: Column<Instant?> = timestampWithTimeZone("ENDED_AT").nullable()
    val walkingSpeed: Column<PosInt> = posIntWrapper("WALKING_SPEED")
    val interactionRadius: Column<PosInt> = posIntWrapper("INTERACTION_RADIUS")
    val minPlayersToStart: Column<NonNegInt> = nonNegDbWrapper("MIN_PLAYERS_TO_START")
    val logsSent: Column<Boolean> = bool("LOGS_SENT")
    val resource_asset_id = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("RESOURCE_ASSET_ID")
    val character_spreadsheet_id = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("CHARACTER_SPREADSHEET_ID")
    val tiles_spreadsheet_id = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("TILES_SPREADSHEET_ID")

    override fun toDomain(resultRow: ResultRow): GameSessionDto = GameSessionDto(
        resultRow[id],
        resultRow[name],
        resultRow[shortName],
        resultRow[walkingSpeed],
        nonEmptyMapOf(
            FileType.MAP to resultRow[mapId],
            FileType.CHARACTER_ASSET_FILE to resultRow[character_spreadsheet_id],
            FileType.TILE_ASSET_FILE to resultRow[tiles_spreadsheet_id],
            FileType.RESOURCE_ASSET_FILE to resultRow[resource_asset_id]
        ),
        resultRow[timeForGame],
        resultRow[minPlayersToStart],
        resultRow[interactionRadius],
        resultRow[maxTimeTokens],
        resultRow[defaultMoneyValue]
    )

    override val domainColumns: List<Expression<*>> = listOf(
        id,
        name,
        shortName,
        walkingSpeed,
        mapId,
        character_spreadsheet_id,
        tiles_spreadsheet_id,
        resource_asset_id,
        timeForGame,
        minPlayersToStart,
        maxTimeTokens,
        defaultMoneyValue,
        interactionRadius
    )

    fun getTimeLeft(rs: ResultRow): Option<TimestampMillis> =
        rs[startedAt].toOption().map { started ->
            val scheduledEnding = started.plusMillis(rs[timeForGame].value)
            val currentDateTime = Instant.now()
            if (scheduledEnding.isBefore(currentDateTime)) {
                TimestampMillis(0L)
            } else {
                TimestampMillis(scheduledEnding.toEpochMilli().minus(currentDateTime.toEpochMilli()))
            }
        }
}
