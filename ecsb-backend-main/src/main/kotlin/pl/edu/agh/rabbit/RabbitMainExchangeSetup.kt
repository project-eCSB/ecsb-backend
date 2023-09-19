package pl.edu.agh.rabbit

import com.rabbitmq.client.Channel
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.utils.ExchangeType

object RabbitMainExchangeSetup {

    fun setup(channel: Channel) {
        channel.exchangeDeclare(InteractionProducer.MAIN_EXCHANGE, ExchangeType.FANOUT.value)

        channel.exchangeDeclare(InteractionProducer.GAME_EXCHANGE, ExchangeType.TOPIC.value)
        channel.exchangeBind(InteractionProducer.GAME_EXCHANGE, InteractionProducer.MAIN_EXCHANGE, "")

        val analyticsQueueName = "analytics-queue"
        channel.queueDeclare(analyticsQueueName, true, false, false, mapOf())
        channel.queueBind(analyticsQueueName, InteractionProducer.MAIN_EXCHANGE, "")
    }
}
