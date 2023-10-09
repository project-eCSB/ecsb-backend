package pl.edu.agh.timer

import arrow.core.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.TimeState
import pl.edu.agh.time.domain.TimeTokenIndex
import pl.edu.agh.time.table.PlayerTimeTokenTable
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.PosInt.Companion.pos

object PlayerTimeTokenDao {
    fun getUpdatedTokens(): Option<NonEmptyMap<GameSessionId, NonEmptyMap<PlayerId, NonEmptyMap<TimeTokenIndex, TimeState>>>> =
        """
        with times as (select ptt.game_session_id,
                  ptt.player_id,
                  ptt.actual_state,
                  (extract(epoch from (now() - ptt.last_used)) * 1000) / regen_time as part_done,
                  ptt.index
           from player_time_token ptt
                    inner join game_user gu on ptt.game_session_id = gu.game_session_id and ptt.player_id = gu.name
                    inner join game_session_user_classes gsuc
                               on gsuc.game_session_id = gu.game_session_id and gsuc.class_name = gu.class_name 
                               for update)
        update player_time_token ptt
        set actual_state = case
                               when floor(t2.part_done * ptt.max_state) > ptt.max_state then ptt.max_state
                               else floor(t2.part_done * ptt.max_state) end
        from times t2
        where ptt.game_session_id = t2.game_session_id
          and ptt.player_id = t2.player_id
          and ptt.actual_state < ptt.max_state
          and ptt.index = t2.index
        returning 
            t2.actual_state as old_actual_state, 
            case when floor(t2.part_done * ptt.max_state) > ptt.max_state then ptt.max_state else floor(t2.part_done * ptt.max_state) end as new_actual_state,
            ptt.index,
            ptt.game_session_id,
            ptt.player_id,
            ptt.max_state;
        """.trimIndent().execAndMap { rs ->
            val oldActualAmount = rs.getInt("old_actual_state")
            val newActualAmount = rs.getInt("new_actual_state")
            val maxAmount = rs.getInt("max_state")
            val playerId = rs.getString("player_id")
            val gameSessionId = rs.getInt("game_session_id")
            val index = rs.getInt("index")

            if (oldActualAmount == newActualAmount) {
                none()
            } else {
                Tuple4(
                    GameSessionId(gameSessionId),
                    PlayerId(playerId),
                    TimeTokenIndex(index),
                    TimeState(newActualAmount.nonNeg, maxAmount.pos)
                ).some()
            }
        }.filterOption()
            .groupBy { (gameSessionId, _, _, _) -> gameSessionId }
            .mapValues { it.value.groupBy { (_, playerId, _, _) -> playerId } }
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
        PlayerTimeTokenTable
            .select {
                (PlayerTimeTokenTable.gameSessionId eq gameSessionId) and (PlayerTimeTokenTable.playerId eq playerId)
            }
            .toDomain(PlayerTimeTokenTable)
            .toNonEmptyMapOrNone()
}
