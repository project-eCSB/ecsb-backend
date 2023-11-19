package pl.edu.agh.utils

import arrow.core.getOrElse
import arrow.core.none
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.time.domain.TimeState
import pl.edu.agh.time.domain.TimeTokenIndex
import pl.edu.agh.time.table.TimeTokensUsedInfo
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.PosInt.Companion.pos
import kotlin.math.max
import kotlin.math.min

// Use with care (or don't use it at all)
class TimeTokenDecreaseStatement<A1, A2>(
    table: Table,
    val gameSessionId: GameSessionId,
    val playerId: PlayerId,
    val amount: PosInt,
    private val amountPerToken: Int = 50
) : UpdateBuilder<TimeTokensUsedInfo>(StatementType.SELECT, table.source.targetTables()) {

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): TimeTokensUsedInfo {
        return executeQuery().let { rs ->
            var usedTokenInfo: OptionS<NonEmptyMap<TimeTokenIndex, TimeState>> = none()
            while (rs.next()) {
                val oldActualState = rs.getInt("old_actual_state")
                val newActualState = rs.getInt("new_actual_state")
                val maxState = rs.getInt("max_state")

                val minIndex = newActualState / amountPerToken
                val maxIndex =
                    if (oldActualState == maxState) (maxState / amountPerToken) else (oldActualState / amountPerToken) + 1

                if (oldActualState != newActualState) {
                    usedTokenInfo = (minIndex until maxIndex).map { index ->
                        val tokenState = min(max(newActualState - (index * amountPerToken), 0), 50)
                        TimeTokenIndex(index) to TimeState(tokenState.nonNeg, amountPerToken.pos)
                    }.toNonEmptyMapOrNone()
                }
            }

            return@let TimeTokensUsedInfo(
                amountUsed = usedTokenInfo.map { amount.toNonNeg() }.getOrElse { 0.nonNeg },
                timeTokensUsed = usedTokenInfo
            )
        }
    }

    override fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>> =
        QueryBuilder(true).run {
            registerArgument(IntegerColumnType(), amountPerToken)
            registerArgument(IntegerColumnType(), amount.value)
            registerArgument(IntegerColumnType(), gameSessionId.value)
            registerArgument(VarCharColumnType(), playerId.value)

            listOf(args)
        }

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String =
        with(QueryBuilder(prepared)) {
            +"with times as (select ptt.game_session_id, ptt.player_id,ptt.actual_state,"
            +"(gsuc.regen_time * interval '1 millisecond')::interval as change_interval,"
            registerArgument(IntegerColumnType(), amountPerToken)
            +" as amount_per_token,"
            registerArgument(IntegerColumnType(), amount.value)
            +" as token_amount, gs.max_time_amount"
            +""" from player_time_token ptt
                    inner join game_user gu on ptt.game_session_id = gu.game_session_id and ptt.player_id = gu.name
                    inner join game_session_user_classes gsuc
                        on gsuc.game_session_id = gu.game_session_id and gsuc.class_name = gu.class_name
                    inner join game_session gs on gsuc.game_session_id = gs.id
                    where """
            +" ptt.game_session_id = "
            registerArgument(IntegerColumnType(), gameSessionId.value)
            +" and ptt.player_id = "
            registerArgument(VarCharColumnType(), playerId.value)
            +" for update)"
            +"""
            update player_time_token npt
            set alter_date   = case
                       when t.max_time_amount - t.token_amount >= 0 then case
                           when npt.actual_state = npt.max_state
                               then now() - (change_interval * (t.max_time_amount - t.token_amount))
                               else case
                                   when (npt.alter_date + (t.change_interval * t.token_amount)) <= now()
                                   then (npt.alter_date + (t.change_interval * t.token_amount))
                               else npt.alter_date end
                           end
                       else npt.alter_date end,
                actual_state = case
                       when t.max_time_amount - t.token_amount >= 0 then
                           case when npt.actual_state = npt.max_state then 
                                npt.actual_state - (t.amount_per_token * t.token_amount)
                           else 
                               case
                                   when (npt.alter_date + (t.change_interval * t.token_amount)) <= now()
                                       then case
                                           when npt.actual_state - (t.amount_per_token * t.token_amount) < 0 then 0
                                           else npt.actual_state - (t.amount_per_token * t.token_amount) end
                                   else npt.actual_state 
                               end
                           end
                       else npt.actual_state end
            from times t
            where npt.game_session_id = t.game_session_id
              and npt.player_id = t.player_id
            returning t.actual_state as old_actual_state, npt.actual_state as new_actual_state, npt.max_state as max_state;
            """

            toString()
        }
}
