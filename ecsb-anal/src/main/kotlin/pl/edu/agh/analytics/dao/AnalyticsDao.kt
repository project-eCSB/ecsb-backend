package pl.edu.agh.analytics.dao

import org.jetbrains.exposed.sql.insert
import pl.edu.agh.analytics.table.AnalyticsTable
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.toPgJson
import java.time.LocalDateTime
import java.time.ZoneOffset

object AnalyticsDao {
    fun insertNewRow(gameSessionId: GameSessionId, senderId: PlayerId, sentAt: LocalDateTime, message: String) {
        AnalyticsTable.insert {
            it[AnalyticsTable.id] = gameSessionId
            it[AnalyticsTable.senderId] = senderId
            it[AnalyticsTable.sentAt] = sentAt.toInstant(ZoneOffset.UTC)
            it[AnalyticsTable.message] = message.toPgJson()
        }
    }


}
