package pl.edu.agh.timer

import arrow.continuations.SuspendApp
import arrow.core.Either
import arrow.fx.coroutines.parZip
import arrow.fx.coroutines.resourceScope
import kotlinx.coroutines.awaitCancellation
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.interaction.service.InteractionConsumerFactory
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.rabbit.RabbitFactory
import pl.edu.agh.rabbit.RabbitMainExchangeSetup
import pl.edu.agh.time.domain.TimeInternalMessages
import pl.edu.agh.utils.ConfigUtils
import pl.edu.agh.utils.DatabaseConnector
import pl.edu.agh.utils.ExchangeType
import pl.edu.agh.utils.getLogger

fun main(): Unit = SuspendApp {
    val timerConfig = ConfigUtils.getConfigOrThrow<TimerConfig>()
    val logger = getLogger(TimerService::class.java)

    resourceScope {
        DatabaseConnector.initDBAsResource().bind()

        val connection = RabbitFactory.getConnection(timerConfig.rabbitConfig).bind()

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

        val equipmentChangeProducer: InteractionProducer<EquipmentInternalMessage> =
            InteractionProducer.create(
                EquipmentInternalMessage.serializer(),
                InteractionProducer.EQ_CHANGE_EXCHANGE,
                ExchangeType.SHARDING,
                connection
            ).bind()

        InteractionConsumerFactory.create<TimeInternalMessages>(
            TimerService(systemOutputProducer),
            System.getProperty("rabbitHostTag", "develop"),
            connection
        ).bind()

        val timeTokenRefreshTask = TimeTokenRefreshTask(systemOutputProducer, equipmentChangeProducer)

        parZip({
            Either.catch {
                timeTokenRefreshTask.refreshSessionTimes()
            }.onLeft {
                logger.error("Error while refreshing session times", it)
            }
        }, {
            Either.catch {
                timeTokenRefreshTask.refreshTimeTokens()
            }.onLeft {
                logger.error("Error while refreshing time tokens", it)
            }
        }, {
            Either.catch {
                timeTokenRefreshTask.sendEndGame()
            }.onLeft {
                logger.error("Error while sending game end messages", it)
            }
        }, { _, _, _ -> })

        awaitCancellation()
    }
}
