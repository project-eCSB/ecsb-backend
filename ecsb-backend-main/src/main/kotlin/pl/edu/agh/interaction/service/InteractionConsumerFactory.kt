package pl.edu.agh.interaction.service

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.resource
import com.rabbitmq.client.Connection
import pl.edu.agh.messages.service.rabbit.JsonRabbitConsumer
import pl.edu.agh.rabbit.RabbitFactory
import pl.edu.agh.utils.LoggerDelegate
import java.util.*

/**
 * Creates RabbitMQ channel and bind queue for given InteractionConsumer (via JsonRabbitConsumer)
 */
object InteractionConsumerFactory {
    private val logger by LoggerDelegate()

    fun <T> create(
        interactionConsumer: InteractionConsumer<T>,
        hostTag: String,
        rabbitMQConnection: Connection
    ): Resource<Unit> = resource {
        val channel = RabbitFactory.getChannelResource(rabbitMQConnection).bind()
        val queueName = interactionConsumer.consumeQueueName(hostTag)
        logger.info("Start consuming messages")
        interactionConsumer.bindQueue(channel, queueName)
        val consumerTag = UUID.randomUUID().toString().substring(0, 7)
        channel.basicConsume(
            queueName,
            true,
            JsonRabbitConsumer<T>(
                channel,
                interactionConsumer
            )
        )
        logger.info("[$consumerTag] Waiting for messages...")
    }
}
