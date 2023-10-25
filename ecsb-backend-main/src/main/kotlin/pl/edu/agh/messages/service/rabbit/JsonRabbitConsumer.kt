package pl.edu.agh.messages.service.rabbit

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import pl.edu.agh.interaction.domain.BetterMessage
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.utils.LoggerDelegate
import java.nio.charset.StandardCharsets

/**
 * Deserialize message from RabbitMQ channel and pass it to according
 * @see InteractionConsumer.callback
*/
class JsonRabbitConsumer<T>(
    channel: Channel,
    private val interactionConsumer: InteractionConsumer<T>
) : DefaultConsumer(channel) {

    private val logger by LoggerDelegate()

    override fun handleDelivery(
        consumerTag: String?,
        envelope: Envelope?,
        properties: AMQP.BasicProperties?,
        body: ByteArray?
    ) {
        try {
            if (body is ByteArray) {
                val messageStr = String(body, StandardCharsets.UTF_8)
                logger.trace("[$consumerTag] Received message: '$messageStr'")
                val message = Json.decodeFromString(
                    BetterMessage.serializer(interactionConsumer.tSerializer),
                    messageStr
                )
                runBlocking {
                    interactionConsumer.callback(
                        message.gameSessionId,
                        message.senderId,
                        message.sentAt,
                        message.message
                    )
                }
            } else {
                error("Body is null")
            }
        } catch (e: Exception) {
            logger.error("Unknown exception on consumer", e)
        }
    }
}
