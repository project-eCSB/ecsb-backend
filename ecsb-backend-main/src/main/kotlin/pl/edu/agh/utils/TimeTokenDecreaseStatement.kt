package pl.edu.agh.utils

import arrow.core.getOrElse
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.time.domain.TimeState
import pl.edu.agh.time.domain.TimeTokenIndex
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.time.table.TimeTokensUsedInfo
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.PosInt.Companion.pos

// Use with care (or don't use it at all)
class TimeTokenDecreaseStatement(
    table: Table,
    val gameSessionId: GameSessionId,
    val playerId: PlayerId,
    val amount: PosInt,
    val regenTime: TimestampMillis,
) : UpdateBuilder<TimeTokensUsedInfo>(StatementType.SELECT, table.source.targetTables()) {

    override fun PreparedStatementApi.executeInternal(transaction: Transaction): TimeTokensUsedInfo {
        return executeQuery().let { rs ->
            val map: MutableMap<TimeTokenIndex, TimeState> = mutableMapOf()
            while (rs.next()) {
                val newActualState = rs.getInt("new_actual_state")
                val tokenIndex = rs.getInt("token_index")
                val maxState = rs.getInt("max_state")
                map[TimeTokenIndex(tokenIndex)] = TimeState(newActualState.nonNeg, maxState.pos)
            }
            val usedTokenInfo = NonEmptyMap.fromMapSafe(map)
            return@let TimeTokensUsedInfo(
                amountUsed = usedTokenInfo.map { amount.toNonNeg() }.getOrElse { 0.nonNeg },
                timeTokensUsed = usedTokenInfo
            )
        }
    }

    override fun arguments(): Iterable<Iterable<Pair<IColumnType, Any?>>> =
        QueryBuilder(true).run {
            registerArgument(IntegerColumnType(), gameSessionId.value)
            registerArgument(VarCharColumnType(), playerId.value)
            registerArgument(IntegerColumnType(), amount.value)
            registerArgument(LongColumnType(), regenTime.value)
            listOf(args)
        }

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String =
        with(QueryBuilder(prepared)) {
            +"""with times as (select ptt.game_session_id, ptt.player_id, ptt.token_index from player_time_token ptt
                    inner join game_session gs on ptt.game_session_id = gs.id
                    where ptt.game_session_id = """
            registerArgument(IntegerColumnType(), gameSessionId.value)
            +" and ptt.player_id = "
            registerArgument(VarCharColumnType(), playerId.value)
            +" and ptt.regen_time is null order by ptt.token_index limit "
            registerArgument(IntegerColumnType(), amount.value)
            +" for update)"
            +"""      
            update player_time_token npt
            set 
                alter_date = now(), 
                actual_state = 0,
                regen_time = """
            registerArgument(LongColumnType(), regenTime.value)
            +""" from times t
            where npt.game_session_id = t.game_session_id
                and npt.player_id = t.player_id
                and npt.token_index = t.token_index
            returning 
                npt.token_index as token_index,
                npt.actual_state as new_actual_state,
                npt.max_state as max_state;
            """
            toString()
        }
}
