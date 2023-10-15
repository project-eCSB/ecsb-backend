package pl.edu.agh.time.table

import arrow.core.none
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.TransactionManager
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.TimeState
import pl.edu.agh.time.domain.TimeTokenIndex
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.literal
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.NonNegInt.Companion.nonNegDbWrapper
import pl.edu.agh.utils.PosInt.Companion.posIntWrapper
import java.time.Instant

object PlayerTimeTokenTable : Table("PLAYER_TIME_TOKEN"), Domainable<Pair<TimeTokenIndex, TimeState>> {
    val gameSessionId: Column<GameSessionId> = intWrapper(GameSessionId::value, ::GameSessionId)("GAME_SESSION_ID")
    val playerId: Column<PlayerId> = stringWrapper(PlayerId::value, ::PlayerId)("PLAYER_ID")
    val index: Column<TimeTokenIndex> = intWrapper(TimeTokenIndex::index, ::TimeTokenIndex)("INDEX")
    val actualState: Column<NonNegInt> = nonNegDbWrapper("ACTUAL_STATE")
    val maxState: Column<PosInt> = posIntWrapper("MAX_STATE")
    val lastUsed: Column<Instant?> = timestampWithTimeZone("LAST_USED").nullable()

    override fun toDomain(resultRow: ResultRow): Pair<TimeTokenIndex, TimeState> =
        resultRow[index] to TimeState(
            resultRow[actualState],
            resultRow[maxState]
        )

    override val domainColumns: List<Expression<*>> = listOf(index, actualState, maxState)

    // Use with care (or don't use it at all)
    fun decreasePlayerTimeTokensQuery(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        amount: PosInt
    ): TimeTokensUsedInfo {
        return TimeTokenDecreaseStatement<NonNegInt, Instant?>(
            PlayerTimeTokenTable,
            where = (PlayerTimeTokenTable.gameSessionId eq gameSessionId) and (PlayerTimeTokenTable.playerId eq playerId) and (PlayerTimeTokenTable.actualState eq PlayerTimeTokenTable.maxState),
            limit = amount.value,
            orderColumn = PlayerTimeTokenTable.index,
            joinColumns = listOf(
                PlayerTimeTokenTable.index,
                PlayerTimeTokenTable.gameSessionId,
                PlayerTimeTokenTable.playerId
            ),
            updateObjects = listOf(
                PlayerTimeTokenTable.actualState to 0.nonNeg.literal(),
                PlayerTimeTokenTable.lastUsed to LiteralOp(JavaTimestampWithTimeZoneColumnType(), Instant.now())
            )
        ).run {
            println(this.prepareSQL(TransactionManager.current()))
            execute(TransactionManager.current())!!
        }
    }
}

data class TimeTokensUsedInfo(
    val amountUsed: NonNegInt,
    val timeTokensUsed: OptionS<NonEmptyMap<TimeTokenIndex, TimeState>>
) {
    companion object {
        val empty: TimeTokensUsedInfo = TimeTokensUsedInfo(0.nonNeg, none())
    }
}
