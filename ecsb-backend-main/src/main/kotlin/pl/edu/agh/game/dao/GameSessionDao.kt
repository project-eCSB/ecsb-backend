package pl.edu.agh.game.dao

import arrow.core.Option
import arrow.core.firstOrNone
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.Direction
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.GameSessionDto
import pl.edu.agh.game.table.GameSessionTable

object GameSessionDao {
    fun createGameSession(
        charactersSpreadsheetUrl: String,
        gameName: String,
        coords: Coordinates,
        direction: Direction,
        loginUserId: LoginUserId
    ): GameSessionId =
        GameSessionTable.insert {
            it[name] = gameName
            it[characterSpriteUrl] = charactersSpreadsheetUrl
            it[startingDirection] = direction.name
            it[startingX] = coords.x
            it[startingY] = coords.y
            it[createdBy] = loginUserId
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
