package pl.edu.agh.interaction.service

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.resource
import pl.edu.agh.messages.service.rabbit.JsonRabbitConsumer
import pl.edu.agh.rabbit.RabbitConfig
import pl.edu.agh.rabbit.RabbitFactory
import pl.edu.agh.utils.LoggerDelegate
import java.util.*

class InteractionConsumer() {

    companion object {
        private val logger by LoggerDelegate()

        fun <T> create(
            rabbitConfig: RabbitConfig,
            interactionConsumerCallback: InteractionConsumerCallback<T>,
            hostTag: String
        ): Resource<Unit> = resource {
            val channel = RabbitFactory.getChannelResource(rabbitConfig).bind()
            val queueName = interactionConsumerCallback.consumeQueueName(hostTag)
            logger.info("Start consuming messages")
            interactionConsumerCallback.bindQueues(channel, queueName)
            val consumerTag = UUID.randomUUID().toString().substring(0, 7)
            channel.basicConsume(
                queueName,
                true,
                JsonRabbitConsumer<T>(
                    channel,
                    interactionConsumerCallback
                )
            )
            logger.info("[$consumerTag] Waiting for messages...")
        }
    }
}
