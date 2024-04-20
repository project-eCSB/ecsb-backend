package pl.edu.agh.analytics.service

import arrow.core.NonEmptyList
import arrow.core.Option
import pl.edu.agh.analytics.dao.AnalyticsDao
import pl.edu.agh.analytics.dao.Logs
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.Transactor
import java.time.LocalDateTime

interface AnalyticsService {
    suspend fun saveLog(gameSessionId: GameSessionId, senderId: PlayerId, sentAt: LocalDateTime, message: String)
    suspend fun getLogs(gameSessionId: GameSessionId): Option<NonEmptyList<Logs>>
}

class AnalyticsServiceImpl : AnalyticsService {
    override suspend fun saveLog(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        sentAt: LocalDateTime,
        message: String
    ) {
        Transactor.dbQuery {
            AnalyticsDao.insertNewRow(gameSessionId, senderId, sentAt, message)
        }
    }

    override suspend fun getLogs(gameSessionId: GameSessionId): Option<NonEmptyList<Logs>> {
        return Transactor.dbQuery {
            AnalyticsDao.getAllLogs(gameSessionId)
        }
    }
}
