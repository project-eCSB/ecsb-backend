package pl.edu.agh

import arrow.continuations.SuspendApp
import arrow.fx.coroutines.resourceScope
import kotlinx.coroutines.awaitCancellation
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.coop.redis.CoopStatesDataConnectorImpl
import pl.edu.agh.coop.service.CoopGameEngineService
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.equipmentChanges.service.EquipmentChangesConsumer
import pl.edu.agh.interaction.service.InteractionConsumerFactory
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.rabbit.RabbitFactory
import pl.edu.agh.redis.RedisJsonConnector
import pl.edu.agh.trade.domain.TradeInternalMessages
import pl.edu.agh.trade.redis.TradeStatesDataConnectorImpl
import pl.edu.agh.trade.service.TradeGameEngineService
import pl.edu.agh.utils.ConfigUtils
import pl.edu.agh.utils.DatabaseConnector
import pl.edu.agh.utils.ExchangeType

fun main(): Unit = SuspendApp {
    val gameEngineConfig = ConfigUtils.getConfigOrThrow<GameEngineConfig>()

    resourceScope {
        val redisCoopStatesConnector = RedisJsonConnector.createAsResource(
            RedisJsonConnector.Companion.CoopStatesCreationParams(gameEngineConfig.redis)
        ).bind()

        val redisTradeStatesConnector = RedisJsonConnector.createAsResource(
            RedisJsonConnector.Companion.TradeStatesCreationParams(gameEngineConfig.redis)
        ).bind()

        val coopStatesDataConnector = CoopStatesDataConnectorImpl(redisCoopStatesConnector)
        val tradeStatesDataConnector = TradeStatesDataConnectorImpl(redisTradeStatesConnector)

        DatabaseConnector.initDBAsResource().bind()

        val connection = RabbitFactory.getConnection(gameEngineConfig.rabbit).bind()

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

        val coopInternalMessageProducer: InteractionProducer<CoopInternalMessages> =
            InteractionProducer.create(
                CoopInternalMessages.serializer(),
                InteractionProducer.COOP_MESSAGES_EXCHANGE,
                ExchangeType.SHARDING,
                connection
            ).bind()

        val hostTag = System.getProperty("rabbitHostTag", "develop")

        InteractionConsumerFactory.create<CoopInternalMessages>(
            CoopGameEngineService(coopStatesDataConnector, systemOutputProducer, equipmentChangeProducer),
            hostTag,
            connection
        ).bind()

        InteractionConsumerFactory.create<TradeInternalMessages.UserInputMessage>(
            TradeGameEngineService(tradeStatesDataConnector, systemOutputProducer),
            hostTag,
            connection
        ).bind()

        InteractionConsumerFactory.create<EquipmentInternalMessage>(
            EquipmentChangesConsumer(coopInternalMessageProducer, systemOutputProducer, coopStatesDataConnector),
            hostTag,
            connection
        ).bind()

        awaitCancellation()
    }
}
