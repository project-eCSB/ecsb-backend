package pl.edu.agh.time.dao

import arrow.core.Option
import arrow.core.Tuple5
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.time.domain.TimeState
import pl.edu.agh.time.domain.TimeTokenIndex
import pl.edu.agh.time.table.PlayerTimeTokenTable
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.PosInt.Companion.pos
import pl.edu.agh.utils.execAndMap
import pl.edu.agh.utils.toNonEmptyMapOrNone
import pl.edu.agh.utils.toNonEmptyMapUnsafe

object PlayerTimeTokenDao {
    fun getUpdatedTokens(): Option<NonEmptyMap<GameSessionId, NonEmptyMap<PlayerId, NonEmptyMap<TimeTokenIndex, TimeState>>>> =
        """
            with times as (select ptt.game_session_id, ptt.player_id, ptt.token_index, ptt.actual_state,
                           (extract(epoch from (now() - ptt.alter_date)) * 1000) / ptt.regen_time as part_done
                           from player_time_token ptt
                                where ptt.actual_state < ptt.max_state
                           for update)
            update player_time_token ntt
            set actual_state = least(floor(t2.part_done * ntt.max_state), ntt.max_state),
                regen_time = 
                    case
                        when floor(t2.part_done * ntt.max_state) >= ntt.max_state then null
                        else ntt.regen_time
                    end
            from times t2
            where ntt.game_session_id = t2.game_session_id
              and ntt.player_id = t2.player_id
              and ntt.token_index = t2.token_index
            returning
                t2.actual_state as old_actual_state,
                least(floor(t2.part_done * ntt.max_state), ntt.max_state) as new_actual_state,
                ntt.game_session_id,
                ntt.player_id,
                ntt.token_index,
                ntt.max_state;
        """.trimIndent().execAndMap { rs ->
            val oldActualState = rs.getInt("old_actual_state")
            val newActualState = rs.getInt("new_actual_state")
            val maxState = rs.getInt("max_state")
            val playerId = rs.getString("player_id")
            val gameSessionId = rs.getInt("game_session_id")
            val tokenIndex = rs.getInt("token_index")
            Tuple5(
                GameSessionId(gameSessionId),
                PlayerId(playerId),
                TimeTokenIndex(tokenIndex),
                TimeState(newActualState.nonNeg, maxState.pos),
                newActualState - oldActualState
            )
        }.filter { it.fifth > 0 }
            .groupBy { (gameSessionId, _) -> gameSessionId }
            .mapValues { it.value.groupBy { (_, playerId, _) -> playerId } }
            .mapValues {
                it.value.mapValues {
                    it.value.map { (_, _, index, timeState) ->
                        index to timeState
                    }.toNonEmptyMapUnsafe()
                }.toNonEmptyMapUnsafe()
            }.toNonEmptyMapOrNone()

    fun getPlayerTokens(
        gameSessionId: GameSessionId,
        playerId: PlayerId
    ): Option<NonEmptyMap<TimeTokenIndex, TimeState>> =
        PlayerTimeTokenTable.select {
            (PlayerTimeTokenTable.gameSessionId eq gameSessionId) and (PlayerTimeTokenTable.playerId eq playerId)
        }.adjustSlice {
            slice(
                PlayerTimeTokenTable.timeTokenIndex,
                PlayerTimeTokenTable.actualState,
                PlayerTimeTokenTable.maxState
            )
        }.map { rs ->
            val tokenIndex = rs[PlayerTimeTokenTable.timeTokenIndex]
            val actualState = rs[PlayerTimeTokenTable.actualState]
            val maxState = rs[PlayerTimeTokenTable.maxState]
            tokenIndex to TimeState(actualState, maxState)
        }.toNonEmptyMapOrNone()
}
