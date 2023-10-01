package pl.edu.agh.timer

import arrow.continuations.SuspendApp
import arrow.fx.coroutines.resourceScope
import kotlinx.coroutines.awaitCancellation
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.interaction.service.InteractionConsumerFactory
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.rabbit.RabbitFactory
import pl.edu.agh.rabbit.RabbitMainExchangeSetup
import pl.edu.agh.time.domain.TimeInternalMessages
import pl.edu.agh.utils.ConfigUtils
import pl.edu.agh.utils.DatabaseConnector
import pl.edu.agh.utils.ExchangeType

fun main(): Unit = SuspendApp {
    val timerConfig = ConfigUtils.getConfigOrThrow<TimerConfig>()

    resourceScope {
        DatabaseConnector.initDBAsResource().bind()

        val connection = RabbitFactory.getConnection(timerConfig.rabbit).bind()

        RabbitFactory.getChannelResource(connection).use {
            RabbitMainExchangeSetup.setup(it)
        }

        val systemOutputProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage> =
            InteractionProducer.create(
                ChatMessageADT.SystemOutputMessage.serializer(),
                InteractionProducer.INTERACTION_EXCHANGE,
                ExchangeType.FANOUT,
                connection
            ).bind()

        InteractionConsumerFactory.create<TimeInternalMessages>(
            TimerService(systemOutputProducer),
            System.getProperty("rabbitHostTag", "develop"),
            connection
        ).bind()

        TimeTokenRefreshTask(systemOutputProducer).refreshSessionTimes().bind()

        TimeTokenRefreshTask(systemOutputProducer).refreshTimeTokens().bind()

        awaitCancellation()
    }
}
