package pl.edu.agh.game.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.auth.domain.loginUserId
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.GameSessionDto
import pl.edu.agh.utils.intWrapper

object GameSessionTable : Table("GAME_SESSION") {
    val id: Column<GameSessionId> = intWrapper(GameSessionId::value, ::GameSessionId)("ID").autoIncrement()
    val name: Column<String> = varchar("NAME", 255)
    val characterSpriteUrl: Column<String> = varchar("CHARACTER_SPRITE_URL", 255)
    val mapId: Column<SavedAssetsId> = intWrapper(SavedAssetsId::value, ::SavedAssetsId)("MAP_ID")
    val shortName: Column<String> = varchar("SHORT_CODE", 255)
    val createdBy: Column<LoginUserId> = loginUserId("CREATED_BY")
    val defaultTimeValue: Column<Int> = integer("DEFAULT_TIME_VALUE")
    val defaultMoneyValue: Column<Int> = integer("DEFAULT_MONEY_VALUE")

    fun toDomain(rs: ResultRow): GameSessionDto = GameSessionDto(
        rs[id],
        rs[name],
        rs[characterSpriteUrl],
        rs[shortName]
    )
}
