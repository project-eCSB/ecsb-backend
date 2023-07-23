package pl.edu.agh.messages.service.rabbit

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import pl.edu.agh.interaction.domain.BetterMessage
import pl.edu.agh.interaction.service.InteractionConsumerCallback
import pl.edu.agh.utils.LoggerDelegate
import java.nio.charset.StandardCharsets

class JsonRabbitConsumer<T>(
    channel: Channel,
    private val interactionConsumerCallback: InteractionConsumerCallback<T>
) :
    DefaultConsumer(channel) {

    private val logger by LoggerDelegate()

    override fun handleDelivery(
        consumerTag: String?,
        envelope: Envelope?,
        properties: AMQP.BasicProperties?,
        body: ByteArray?
    ) {
        val realRoutingKey = properties!!.headers["x-real-routing-key"]
        try {
            if (body is ByteArray) {
                val messageStr = String(body, StandardCharsets.UTF_8)
                logger.info("[$consumerTag] Received message: '$messageStr'")
                val message =
                    Json.decodeFromString(BetterMessage.serializer(interactionConsumerCallback.tSerializer), messageStr)
                runBlocking {
                    interactionConsumerCallback.callback(
                        message.gameSessionId,
                        message.senderId,
                        message.sentAt,
                        message.message
                    )
                }
            } else {
                throw IllegalStateException("Body is null")
            }
        } catch (e: Exception) {
            logger.error("Unknown exception on consumer", e)
        }
    }
}
