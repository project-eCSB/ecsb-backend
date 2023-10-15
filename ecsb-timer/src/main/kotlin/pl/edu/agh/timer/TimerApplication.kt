package pl.edu.agh.timer

import arrow.continuations.SuspendApp
import arrow.core.Either
import arrow.fx.coroutines.resourceScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.interaction.service.InteractionConsumerFactory
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.rabbit.RabbitFactory
import pl.edu.agh.rabbit.RabbitMainExchangeSetup
import pl.edu.agh.time.domain.TimeInternalMessages
import pl.edu.agh.utils.*

fun main(): Unit = SuspendApp {
    val timerConfig = ConfigUtils.getConfigOrThrow<TimerConfig>()
    val logger = getLogger(TimerService::class.java)

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

        val timeTokenRefreshTask = TimeTokenRefreshTask(systemOutputProducer)

        coroutineScope {
            launch {
                Either.catch {
                    timeTokenRefreshTask.refreshSessionTimes()
                }.onLeft {
                    logger.error("Error while refreshing session times", it)
                }
            }
            launch {
                Either.catch {
                    timeTokenRefreshTask.refreshTimeTokens()
                }.onLeft {
                    logger.error("Error while refreshing time tokens", it)
                }
            }
        }

        awaitCancellation()
    }
}
