package pl.edu.agh.timer

import arrow.core.Option
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.TimeState
import pl.edu.agh.game.table.GameUserTable
import pl.edu.agh.time.domain.TimeTokenIndex
import pl.edu.agh.time.table.PlayerTimeTokenTable
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg

object PlayerTimeTokenDao {
    fun getUpdatedTokens(): Option<NonEmptyMap<GameSessionId, NonEmptyMap<PlayerId, NonEmptyMap<TimeTokenIndex, TimeState>>>> {
        val result = PlayerTimeTokenTable.updateReturning(
            where = { LessOp(PlayerTimeTokenTable.actualState, PlayerTimeTokenTable.maxState) },
            from = PlayerTimeTokenTable.alias("old_player_time_token"),
            joinColumns = listOf(PlayerTimeTokenTable.gameSessionId, PlayerTimeTokenTable.playerId),
            updateObjects = UpdateObject(PlayerTimeTokenTable.actualState, PlayerTimeTokenTable.actualState + 1.nonNeg),
            returningNew = mapOf(
                PlayerTimeTokenTable.gameSessionId.name to PlayerTimeTokenTable.gameSessionId,
                PlayerTimeTokenTable.index.name to PlayerTimeTokenTable.index,
                PlayerTimeTokenTable.playerId.name to PlayerTimeTokenTable.playerId,
                PlayerTimeTokenTable.maxState.name to PlayerTimeTokenTable.maxState
            )
        ).groupBy { it.returningNew[PlayerTimeTokenTable.gameSessionId.name] as GameSessionId }
            .mapValues { (_, returningList) ->
                returningList.groupBy { it.returningNew[PlayerTimeTokenTable.playerId.name] as PlayerId }
                    .mapValues { (_, playerReturningList) ->
                        playerReturningList.associate { returningObject ->
                            val index = returningObject.returningNew[PlayerTimeTokenTable.index.name] as TimeTokenIndex
                            val timeState = TimeState(
                                returningObject.returningBoth[PlayerTimeTokenTable.actualState.name]?.after as NonNegInt,
                                returningObject.returningNew[PlayerTimeTokenTable.maxState.name] as PosInt
                            )
                            index to timeState
                        }.toNonEmptyMapUnsafe()
                    }.toNonEmptyMapUnsafe()
            }.toNonEmptyMapOrNone()

        return result
    }

    fun getPlayerTokens(
        gameSessionId: GameSessionId,
        playerId: PlayerId
    ): Option<NonEmptyMap<TimeTokenIndex, TimeState>> =
        PlayerTimeTokenTable.select {
            (PlayerTimeTokenTable.gameSessionId eq gameSessionId) and (PlayerTimeTokenTable.playerId eq playerId)
        }.map { PlayerTimeTokenTable.toDomain(it) }.toNonEmptyMapOrNone()
}
