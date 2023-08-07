package pl.edu.agh.coop

import arrow.continuations.SuspendApp
import arrow.fx.coroutines.resourceScope
import kotlinx.coroutines.awaitCancellation
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.coop.domain.GameEngineConfig
import pl.edu.agh.coop.redis.CoopStatesDataConnectorImpl
import pl.edu.agh.coop.service.CoopGameEngineService
import pl.edu.agh.equipment.domain.EquipmentChangeADT
import pl.edu.agh.equipmentChanges.service.EquipmentChangesConsumer
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.trade.domain.TradeInternalMessages
import pl.edu.agh.trade.redis.TradeStatesDataConnectorImpl
import pl.edu.agh.trade.service.TradeGameEngineService
import pl.edu.agh.utils.ConfigUtils
import pl.edu.agh.utils.DatabaseConnector

fun main(): Unit = SuspendApp {
    val gameEngineConfig = ConfigUtils.getConfigOrThrow<GameEngineConfig>()

    resourceScope {
        val redisCoopStatesConnector = RedisHashMapConnector.createAsResource(
            RedisHashMapConnector.Companion.CoopStatesCreationParams(gameEngineConfig.redis)
        ).bind()

        val redisTradeStatesConnector = RedisHashMapConnector.createAsResource(
            RedisHashMapConnector.Companion.TradeStatesCreationParams(gameEngineConfig.redis)
        ).bind()

        val coopStatesDataConnector = CoopStatesDataConnectorImpl(redisCoopStatesConnector)
        val tradeStatesDataConnector = TradeStatesDataConnectorImpl(redisTradeStatesConnector)

        DatabaseConnector.initDBAsResource().bind()

        val interactionProducer = InteractionProducer.create(
            gameEngineConfig.rabbit,
            ChatMessageADT.SystemInputMessage.serializer(),
            InteractionProducer.INTERACTION_EXCHANGE
        ).bind()

        val equipmentChangeProducer: InteractionProducer<EquipmentChangeADT> =
            InteractionProducer.create(
                gameEngineConfig.rabbit,
                EquipmentChangeADT.serializer(),
                InteractionProducer.EQ_CHANGE_EXCHANGE
            ).bind()

        val coopInternalMessageProducer: InteractionProducer<CoopInternalMessages> =
            InteractionProducer.create(
                gameEngineConfig.rabbit,
                CoopInternalMessages.serializer(),
                InteractionProducer.COOP_MESSAGES_EXCHANGE
            ).bind()

        val hostTag = System.getProperty("rabbitHostTag", "develop")

        InteractionConsumer.create<CoopInternalMessages>(
            gameEngineConfig.rabbit,
            CoopGameEngineService(
                coopStatesDataConnector,
                interactionProducer,
                equipmentChangeProducer
            ),
            hostTag
        ).bind()

        InteractionConsumer.create<TradeInternalMessages.UserInputMessage>(
            gameEngineConfig.rabbit,
            TradeGameEngineService(
                tradeStatesDataConnector,
                interactionProducer
            ),
            hostTag
        ).bind()

        InteractionConsumer.create(
            gameEngineConfig.rabbit,
            EquipmentChangesConsumer(coopInternalMessageProducer, coopStatesDataConnector),
            hostTag
        ).bind()

        awaitCancellation()
    }
}
