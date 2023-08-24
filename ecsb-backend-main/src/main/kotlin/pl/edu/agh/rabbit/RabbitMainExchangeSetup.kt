package pl.edu.agh.rabbit

import com.rabbitmq.client.BuiltinExchangeType
import pl.edu.agh.interaction.service.InteractionProducer

object RabbitMainExchangeSetup {

    suspend fun setup(rabbitConfig: RabbitConfig) {
        RabbitFactory.getChannelResource(rabbitConfig).use {
            it.exchangeDeclare(InteractionProducer.MAIN_EXCHANGE, BuiltinExchangeType.FANOUT)

            it.exchangeDeclare(InteractionProducer.GAME_EXCHANGE, BuiltinExchangeType.TOPIC)
            it.exchangeBind(InteractionProducer.GAME_EXCHANGE, InteractionProducer.MAIN_EXCHANGE, "")

            val analyticsQueueName = "analytics-queue"
            it.queueDeclare(analyticsQueueName, true, false, false, mapOf())
            it.queueBind(analyticsQueueName, InteractionProducer.MAIN_EXCHANGE, "")
        }
    }
}
