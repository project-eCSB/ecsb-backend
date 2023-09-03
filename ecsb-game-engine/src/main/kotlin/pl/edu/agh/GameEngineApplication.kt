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

        val systemOutputProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage> =
            InteractionProducer.create(
                gameEngineConfig.rabbit,
                ChatMessageADT.SystemOutputMessage.serializer(),
                InteractionProducer.INTERACTION_EXCHANGE,
                ExchangeType.FANOUT
            ).bind()

        val equipmentChangeProducer: InteractionProducer<EquipmentInternalMessage> =
            InteractionProducer.create(
                gameEngineConfig.rabbit,
                EquipmentInternalMessage.serializer(),
                InteractionProducer.EQ_CHANGE_EXCHANGE,
                ExchangeType.SHARDING
            ).bind()

        val coopInternalMessageProducer: InteractionProducer<CoopInternalMessages> =
            InteractionProducer.create(
                gameEngineConfig.rabbit,
                CoopInternalMessages.serializer(),
                InteractionProducer.COOP_MESSAGES_EXCHANGE,
                ExchangeType.SHARDING
            ).bind()

        val hostTag = System.getProperty("rabbitHostTag", "develop")

        InteractionConsumerFactory.create<CoopInternalMessages>(
            gameEngineConfig.rabbit,
            CoopGameEngineService(coopStatesDataConnector, systemOutputProducer, equipmentChangeProducer),
            hostTag
        ).bind()

        InteractionConsumerFactory.create<TradeInternalMessages.UserInputMessage>(
            gameEngineConfig.rabbit,
            TradeGameEngineService(tradeStatesDataConnector, systemOutputProducer),
            hostTag
        ).bind()

        InteractionConsumerFactory.create<EquipmentInternalMessage>(
            gameEngineConfig.rabbit,
            EquipmentChangesConsumer(coopInternalMessageProducer, coopStatesDataConnector),
            hostTag
        ).bind()

        awaitCancellation()
    }
}
