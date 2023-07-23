package pl.edu.agh.analytics.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.intWrapper
import pl.edu.agh.utils.stringWrapper
import pl.edu.agh.utils.timestampWithTimeZone
import java.time.Instant

object AnalyticsTable : Table("ANAL_LOG") {
    val id: Column<GameSessionId> = intWrapper(GameSessionId::value, ::GameSessionId)("GAME_SESSION_ID").autoIncrement()
    val senderId: Column<PlayerId> = stringWrapper(PlayerId::value, ::PlayerId)("SENDER_ID")
    val message: Column<String> = varchar("MESSAGE", 10000000)
    val sentAt: Column<Instant> = timestampWithTimeZone("SENT_AT")
}
