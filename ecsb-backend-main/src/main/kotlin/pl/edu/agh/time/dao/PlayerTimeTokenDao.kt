package pl.edu.agh.time.dao

import arrow.core.Option
import arrow.core.Tuple4
import arrow.core.firstOrNone
import arrow.core.flatten
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.time.domain.TimeState
import pl.edu.agh.game.dao.GameUserDao
import pl.edu.agh.game.table.GameSessionTable
import pl.edu.agh.time.domain.TimeTokenIndex
import pl.edu.agh.time.table.PlayerTimeTokenTable
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.PosInt.Companion.pos
import pl.edu.agh.utils.execAndMap
import pl.edu.agh.utils.toNonEmptyMapOrNone
import pl.edu.agh.utils.toNonEmptyMapUnsafe
import kotlin.math.max
import kotlin.math.min

object PlayerTimeTokenDao {
    fun getUpdatedTokens(): Option<NonEmptyMap<GameSessionId, NonEmptyMap<PlayerId, NonEmptyMap<TimeTokenIndex, TimeState>>>> =
        """
            with times as (select ptt.game_session_id,
                                  ptt.player_id,
                                  ptt.actual_state,
                                  (extract(epoch from (now() - ptt.alter_date)) * 1000) /
                                  (regen_time * gs.max_time_amount) as part_done,
                                  gs.max_time_amount
                           from player_time_token ptt
                                    inner join game_user gu on ptt.game_session_id = gu.game_session_id and ptt.player_id = gu.name
                                    inner join game_session_user_classes gsuc
                                               on gsuc.game_session_id = gu.game_session_id and gsuc.class_name = gu.class_name
                                    inner join game_session gs on gs.id = gu.game_session_id
                           where ptt.actual_state < ptt.max_state
                               for update)
            update player_time_token ptt
            set actual_state = case
                                   when floor(t2.part_done * ptt.max_state) > ptt.max_state then ptt.max_state
                                   else floor(t2.part_done * ptt.max_state) end
            from times t2
            where ptt.game_session_id = t2.game_session_id
              and ptt.player_id = t2.player_id
              and ptt.actual_state < ptt.max_state
            returning
                t2.actual_state as old_actual_state,
                case
                    when floor(t2.part_done * ptt.max_state) > ptt.max_state then ptt.max_state
                    else floor(t2.part_done * ptt.max_state) end as new_actual_state,
                ptt.game_session_id,
                ptt.player_id,
                ptt.max_state,
                t2.max_time_amount;
        """.trimIndent().execAndMap { rs ->
            val oldActualAmount = rs.getInt("old_actual_state")
            val newActualAmount = rs.getInt("new_actual_state")
            val maxAmount = rs.getInt("max_state")
            val playerId = rs.getString("player_id")
            val gameSessionId = rs.getInt("game_session_id")
            val maxTimeAmount = rs.getInt("max_time_amount")

            if (oldActualAmount == newActualAmount) {
                listOf()
            } else {
                val amountPerToken = maxAmount / maxTimeAmount
                val maxTokensIndexToSend =
                    if (newActualAmount == maxAmount) maxTimeAmount - 1 else newActualAmount / amountPerToken
                val minTokensIndexToSend = oldActualAmount / amountPerToken

                (minTokensIndexToSend..maxTokensIndexToSend).map { index ->
                    val tokenState = min(newActualAmount - (index * amountPerToken), amountPerToken)
                    Tuple4(
                        GameSessionId(gameSessionId),
                        PlayerId(playerId),
                        TimeTokenIndex(index),
                        TimeState(tokenState.nonNeg, amountPerToken.pos)
                    )
                }
            }
        }.flatten()
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
            .join(
                GameSessionTable,
                JoinType.INNER,
                additionalConstraint = { PlayerTimeTokenTable.gameSessionId eq GameSessionTable.id }
            ).select {
                (PlayerTimeTokenTable.gameSessionId eq gameSessionId) and (PlayerTimeTokenTable.playerId eq playerId)
            }
            .adjustSlice {
                slice(
                    GameSessionTable.maxTimeAmount,
                    PlayerTimeTokenTable.actualState,
                    PlayerTimeTokenTable.maxState
                )
            }
            .firstOrNone()
            .map { rs ->
                val maxTimeAmount = rs[GameSessionTable.maxTimeAmount]
                val actualState = rs[PlayerTimeTokenTable.actualState]
                val maxStateAmount = rs[PlayerTimeTokenTable.maxState]

                val amountPerToken = maxStateAmount.value / maxTimeAmount.value

                (0 until maxTimeAmount.value).map { index ->
                    val tokenState =
                        max(
                            min(actualState.value - (index * amountPerToken), GameUserDao.MAX_TIME_TOKEN_STATE.value),
                            0
                        )
                    Pair(
                        TimeTokenIndex(index),
                        TimeState(tokenState.nonNeg, amountPerToken.pos)
                    )
                }
            }.flatMap { it.toNonEmptyMapOrNone() }
}
