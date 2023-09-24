package pl.edu.agh.game.dao

import arrow.core.Option
import arrow.core.firstOrNone
import arrow.core.none
import arrow.core.some
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.GameSessionDto
import pl.edu.agh.game.service.GameAssets
import pl.edu.agh.game.table.GameSessionTable
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.utils.DB

object GameSessionDao {
    fun createGameSession(
        gameName: String,
        gameAssets: GameAssets,
        loginUserId: LoginUserId,
        timeForGame: TimestampMillis,
        maxTimeAmount: TimestampMillis
    ): GameSessionId =
        GameSessionTable.insert {
            it[GameSessionTable.name] = gameName
            it[GameSessionTable.createdBy] = loginUserId
            it[GameSessionTable.mapId] = gameAssets.mapAssetId
            it[GameSessionTable.character_spreadsheet_id] = gameAssets.characterAssetsId
            it[GameSessionTable.tiles_spreadsheet_id] = gameAssets.tileAssetsId
            it[GameSessionTable.resource_asset_id] = gameAssets.resourceAssetsId
            it[GameSessionTable.timeForGame] = timeForGame
            it[GameSessionTable.maxTimeAmount] = maxTimeAmount
        }[GameSessionTable.id]

    fun getGameSession(gameSessionId: GameSessionId): Option<GameSessionDto> =
        GameSessionTable.select {
            GameSessionTable.id eq gameSessionId
        }.firstOrNone().map { GameSessionTable.toDomain(it) }

    fun findGameSession(gameCode: String): Option<GameSessionId> =
        GameSessionTable.select {
            GameSessionTable.shortName eq gameCode
        }.firstOrNone().map { GameSessionTable.toDomain(it).id }

    fun startGame(gameSessionId: GameSessionId): DB<Option<Unit>> = {
        val resultRows =
            GameSessionTable.update({ (GameSessionTable.id eq gameSessionId) and (GameSessionTable.startedAt.isNull()) }) {
                it[GameSessionTable.startedAt] = java.time.Instant.now()
            }

        if (resultRows > 0) {
            Unit.some()
        } else {
            none()
        }
    }
}
