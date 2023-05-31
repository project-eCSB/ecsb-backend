package pl.edu.agh.game.dao

import arrow.core.Option
import arrow.core.firstOrNone
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import pl.edu.agh.assets.domain.SavedAssetsId
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.GameSessionDto
import pl.edu.agh.game.table.GameSessionTable

object GameSessionDao {
    fun createGameSession(
        charactersSpreadsheetUrl: String,
        gameName: String,
        mapId: SavedAssetsId,
        loginUserId: LoginUserId
    ): GameSessionId =
        GameSessionTable.insert {
            it[GameSessionTable.name] = gameName
            it[GameSessionTable.characterSpriteUrl] = charactersSpreadsheetUrl
            it[GameSessionTable.createdBy] = loginUserId
            it[GameSessionTable.mapId] = mapId
        }[GameSessionTable.id]

    fun getGameSession(gameSessionId: GameSessionId): Option<GameSessionDto> =
        GameSessionTable.select {
            GameSessionTable.id eq gameSessionId
        }.firstOrNone().map { GameSessionTable.toDomain(it) }

    fun findGameSession(gameCode: String): Option<GameSessionId> =
        GameSessionTable.select {
            GameSessionTable.shortName eq gameCode
        }.firstOrNone().map { GameSessionTable.toDomain(it).id }
}
