package pl.edu.agh.time.table

import arrow.core.none
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.TransactionManager
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.time.domain.TimeState
import pl.edu.agh.time.domain.TimeTokenIndex
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.NonNegInt.Companion.nonNegDbWrapper
import pl.edu.agh.utils.PosInt.Companion.posIntWrapper
import java.time.Instant

object PlayerTimeTokenTable : Table("PLAYER_TIME_TOKEN") {
    val gameSessionId: Column<GameSessionId> = intWrapper(GameSessionId::value, ::GameSessionId)("GAME_SESSION_ID")
    val playerId: Column<PlayerId> = stringWrapper(PlayerId::value, ::PlayerId)("PLAYER_ID")
    val actualState: Column<NonNegInt> = nonNegDbWrapper("ACTUAL_STATE")
    val maxState: Column<PosInt> = posIntWrapper("MAX_STATE")
    val alterDate: Column<Instant> = timestampWithTimeZone("ALTER_DATE")

    // Use with care (or don't use it at all)
    fun decreasePlayerTimeTokensQuery(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        amount: PosInt
    ): TimeTokensUsedInfo {
        return TimeTokenDecreaseStatement<NonNegInt, Instant?>(
            table = PlayerTimeTokenTable,
            gameSessionId = gameSessionId,
            playerId = playerId,
            amount = amount
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
