package pl.edu.agh.messages.service.rabbit

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import pl.edu.agh.utils.LoggerDelegate
import java.nio.charset.StandardCharsets

class JsonRabbitConsumer<T>(
    val kSerializer: KSerializer<T>,
    channel: com.rabbitmq.client.Channel,
    val callback: suspend (T) -> Unit
) :
    DefaultConsumer(channel) {

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
                logger.info("[$consumerTag] Received message: '$messageStr'")
                val message = Json.decodeFromString(kSerializer, messageStr)
                runBlocking {
                    callback(message)
                }
            } else {
                throw IllegalStateException("Body is null")
            }
        } catch (e: Exception) {
            logger.error("Unknown exception on consumer", e)
        }
    }
}
