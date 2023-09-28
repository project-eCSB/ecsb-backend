package pl.edu.agh.game.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.auth.domain.loginUserId
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.Money
import pl.edu.agh.game.domain.GameSessionDto
import pl.edu.agh.game.service.GameAssets
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.NonNegInt.Companion.nonNegDbWrapper
import pl.edu.agh.utils.intWrapper
import pl.edu.agh.utils.longWrapper
import pl.edu.agh.utils.timestampWithTimeZone
import java.time.Instant

object GameSessionTable : Table("GAME_SESSION") {
    val id: Column<GameSessionId> = intWrapper(GameSessionId::value, ::GameSessionId)("ID").autoIncrement()
    val name: Column<String> = varchar("NAME", 255)
    val mapId: Column<SavedAssetsId> = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("MAP_ID")
    val shortName: Column<String> = varchar("SHORT_CODE", 255)
    val createdBy: Column<LoginUserId> = loginUserId("CREATED_BY")
    val defaultTimeValue: Column<NonNegInt> = nonNegDbWrapper("DEFAULT_TIME_VALUE")
    val defaultMoneyValue: Column<Money> = longWrapper(Money::value, ::Money)("DEFAULT_MONEY_VALUE")
    val maxTimeAmount: Column<NonNegInt> = nonNegDbWrapper("MAX_TIME_AMOUNT")
    val timeForGame: Column<TimestampMillis> = longWrapper(TimestampMillis::value, ::TimestampMillis)("TIME_FOR_GAME")
    val startedAt: Column<Instant?> = timestampWithTimeZone("STARTED_AT").nullable()

    val resource_asset_id = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("RESOURCE_ASSET_ID")
    val character_spreadsheet_id = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("CHARACTER_SPREADSHEET_ID")
    val tiles_spreadsheet_id = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("TILES_SPREADSHEET_ID")

    fun toDomain(rs: ResultRow): GameSessionDto = GameSessionDto(
        rs[id],
        rs[name],
        rs[shortName],
        GameAssets(
            mapAssetId = rs[mapId],
            characterAssetsId = rs[character_spreadsheet_id],
            tileAssetsId = rs[tiles_spreadsheet_id],
            resourceAssetsId = rs[resource_asset_id]
        )

    )
}
