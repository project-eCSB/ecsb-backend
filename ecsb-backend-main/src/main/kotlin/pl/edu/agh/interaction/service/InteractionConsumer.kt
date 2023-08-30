package pl.edu.agh.interaction.service

import com.rabbitmq.client.Channel
import kotlinx.serialization.KSerializer
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import java.time.LocalDateTime

interface InteractionConsumer<T> {
    suspend fun callback(gameSessionId: GameSessionId, senderId: PlayerId, sentAt: LocalDateTime, message: T)
    val tSerializer: KSerializer<T>
    fun consumeQueueName(hostTag: String): String
    fun exchangeName(): String
    fun bindQueue(channel: Channel, queueName: String)
}
