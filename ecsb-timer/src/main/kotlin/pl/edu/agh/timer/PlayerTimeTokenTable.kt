package pl.edu.agh.timer

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.TimeState
import pl.edu.agh.time.domain.TimeTokenIndex
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNegDbWrapper
import pl.edu.agh.utils.PosInt.Companion.posIntWrapper
import java.time.Instant

object PlayerTimeTokenTable : Table("PLAYER_TIME_TOKEN") {
    val gameSessionId: Column<GameSessionId> = intWrapper(GameSessionId::value, ::GameSessionId)("GAME_SESSION_ID")
    val playerId: Column<PlayerId> = stringWrapper(PlayerId::value, ::PlayerId)("PLAYER_ID")
    val index: Column<TimeTokenIndex> = intWrapper(TimeTokenIndex::index, ::TimeTokenIndex)("INDEX")
    val actualState: Column<NonNegInt> = nonNegDbWrapper("ACTUAL_STATE")
    val maxState: Column<PosInt> = posIntWrapper("MAX_STATE")
    val lastUsed: Column<Instant?> = timestampWithTimeZone("LAST_USED").nullable()

    fun toDomain(rs: ResultRow): Pair<TimeTokenIndex, TimeState> =
        rs[index] to TimeState(
            rs[actualState],
            rs[maxState]
        )
}
