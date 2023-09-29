package pl.edu.agh.game.dao

import arrow.core.*
import org.jetbrains.exposed.sql.*
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.GameSessionDto
import pl.edu.agh.game.service.GameAssets
import pl.edu.agh.game.table.GameSessionTable
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.utils.DB
import pl.edu.agh.utils.NonNegInt
import pl.edu.agh.utils.PosInt
import java.time.Instant

object GameSessionDao {
    fun createGameSession(
        gameName: String,
        gameAssets: GameAssets,
        loginUserId: LoginUserId,
        timeForGame: TimestampMillis,
        maxTimeAmount: NonNegInt,
        walkingSpeed: PosInt
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

    fun getGameSessionNameAfterEnd(gameSessionId: GameSessionId): Option<String> =
        GameSessionTable.select {
            (GameSessionTable.id eq gameSessionId)
        }.firstOrNone().flatMap {
            val isAfterEnd = it[GameSessionTable.startedAt]?.plusMillis(it[GameSessionTable.timeForGame].value)
                ?.isAfter(Instant.now()) ?: true

            if (isAfterEnd) {
                it[GameSessionTable.name].some()
            } else {
                none()
            }
        }
}
