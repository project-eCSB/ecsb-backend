package pl.edu.agh.interaction.service

import kotlinx.serialization.KSerializer
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import java.time.LocalDateTime

abstract class InteractionConsumerCallback<T> {
    abstract suspend fun callback(gameSessionId: GameSessionId, senderId: PlayerId, sentAt: LocalDateTime, message: T)
    abstract val tSerializer: KSerializer<T>
    abstract fun consumeQueueName(hostTag: String): String
}
