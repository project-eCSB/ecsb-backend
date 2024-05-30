package pl.edu.agh.analytics.service

import kotlinx.serialization.Serializable
import pl.edu.agh.analytics.dao.Logs
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.utils.NonEmptyListS

@Serializable
data class FeedModelRequest(
    val gameSessionId: GameSessionId,
    val logs: NonEmptyListS<Logs>
)
