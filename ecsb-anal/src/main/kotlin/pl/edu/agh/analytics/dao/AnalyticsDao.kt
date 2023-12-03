package pl.edu.agh.analytics.dao

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import pl.edu.agh.analytics.table.AnalyticsTable
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.DateSerializer
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

object AnalyticsDao {
    fun insertNewRow(gameSessionId: GameSessionId, senderId: PlayerId, sentAt: LocalDateTime, message: String) {
        AnalyticsTable.insert {
            it[AnalyticsTable.id] = gameSessionId
            it[AnalyticsTable.senderId] = senderId
            it[AnalyticsTable.sentAt] = sentAt.toInstant(ZoneOffset.UTC)
            it[AnalyticsTable.message] = message
        }
    }

    fun getAllLogs(gameSessionId: GameSessionId): List<Logs> {
        return AnalyticsTable.slice(AnalyticsTable.senderId, AnalyticsTable.sentAt, AnalyticsTable.message)
            .select { AnalyticsTable.id eq gameSessionId }
            .orderBy(AnalyticsTable.sentAt to SortOrder.DESC)
            .map {
                Logs(
                    it[AnalyticsTable.senderId],
                    LocalDateTime.ofInstant(it[AnalyticsTable.sentAt], ZoneId.systemDefault()),
                    it[AnalyticsTable.message]
                )
            }
    }
}

@Serializable
data class Logs(
    val senderId: PlayerId,
    @Serializable(DateSerializer::class)
    val sentAt: LocalDateTime,
    val message: String
)
