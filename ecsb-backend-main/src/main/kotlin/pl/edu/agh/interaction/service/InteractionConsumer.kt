package pl.edu.agh.interaction.service

import com.rabbitmq.client.Channel
import kotlinx.serialization.KSerializer
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.ExchangeType
import java.time.LocalDateTime

interface InteractionConsumer<T> {
    suspend fun callback(gameSessionId: GameSessionId, senderId: PlayerId, sentAt: LocalDateTime, message: T)
    val tSerializer: KSerializer<T>
    fun consumeQueueName(hostTag: String): String
    fun exchangeName(): String
    fun exchangeType(): ExchangeType
    fun autoDelete(): Boolean
    fun bindQueue(channel: Channel, queueName: String) {
        channel.exchangeDeclare(exchangeName(), exchangeType().value)
        channel.queueDeclare(queueName, true, false, autoDelete(), mapOf())
        channel.queueBind(queueName, exchangeName(), "")
    }
}
