package pl.edu.agh.rabbit

import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.utils.ExchangeType

object RabbitMainExchangeSetup {

    suspend fun setup(rabbitConfig: RabbitConfig) {
        RabbitFactory.getChannelResource(rabbitConfig).use {
            it.exchangeDeclare(InteractionProducer.MAIN_EXCHANGE, ExchangeType.FANOUT.value)

            it.exchangeDeclare(InteractionProducer.GAME_EXCHANGE, ExchangeType.TOPIC.value)
            it.exchangeBind(InteractionProducer.GAME_EXCHANGE, InteractionProducer.MAIN_EXCHANGE, "")

            val analyticsQueueName = "analytics-queue"
            it.queueDeclare(analyticsQueueName, true, false, false, mapOf())
            it.queueBind(analyticsQueueName, InteractionProducer.MAIN_EXCHANGE, "")
        }
    }
}
