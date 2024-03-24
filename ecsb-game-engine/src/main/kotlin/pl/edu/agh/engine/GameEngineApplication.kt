package pl.edu.agh.engine

import arrow.continuations.SuspendApp
import arrow.core.andThen
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.resourceScope
import kotlinx.coroutines.awaitCancellation
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.coop.redis.CoopStatesDataConnectorImpl
import pl.edu.agh.coop.redis.CoopStatesRedisCreationParams
import pl.edu.agh.coop.service.CoopGameEngineService
import pl.edu.agh.coop.service.TravelCoopServiceImpl
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.equipment.service.EquipmentGameEngineService
import pl.edu.agh.equipment.service.PlayerResourceService
import pl.edu.agh.equipmentChangeQueue.service.EquipmentChangeQueueService
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionConsumerFactory
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.production.ProductionGameEngineService
import pl.edu.agh.rabbit.RabbitFactory
import pl.edu.agh.rabbit.RabbitMainExchangeSetup
import pl.edu.agh.redis.RedisJsonConnector
import pl.edu.agh.trade.redis.AdvertisementRedisCreationParams
import pl.edu.agh.trade.redis.AdvertisementStateDataConnectorImpl
import pl.edu.agh.trade.redis.TradeStatesDataConnectorImpl
import pl.edu.agh.trade.redis.TradeStatesRedisCreationParams
import pl.edu.agh.trade.service.EquipmentTradeServiceImpl
import pl.edu.agh.trade.service.TradeGameEngineService
import pl.edu.agh.utils.ConfigUtils
import pl.edu.agh.utils.DatabaseConnector
import pl.edu.agh.utils.ExchangeType

fun main(): Unit = SuspendApp {
    val gameEngineConfig = ConfigUtils.getConfigOrThrow<GameEngineConfig>()

    resourceScope {
        val redisCoopStatesConnector = RedisJsonConnector.createAsResource(
            CoopStatesRedisCreationParams(gameEngineConfig.redisConfig)
        ).bind()

        val redisTradeStatesConnector = RedisJsonConnector.createAsResource(
            TradeStatesRedisCreationParams(gameEngineConfig.redisConfig)
        ).bind()

        val redisAdvertisementDataConnector = RedisJsonConnector.createAsResource(
            AdvertisementRedisCreationParams(gameEngineConfig.redisConfig)
        ).bind()

        val coopStatesDataConnector = CoopStatesDataConnectorImpl(redisCoopStatesConnector)
        val tradeStatesDataConnector = TradeStatesDataConnectorImpl(redisTradeStatesConnector)
        val advertisementDataConnector = AdvertisementStateDataConnectorImpl(redisAdvertisementDataConnector)

        DatabaseConnector.initDBAsResource().bind()

        val connection = RabbitFactory.getConnection(gameEngineConfig.rabbitConfig).bind()

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

        val coopInternalMessageProducer: InteractionProducer<CoopInternalMessages.UserInputMessage> =
            InteractionProducer.create(
                CoopInternalMessages.UserInputMessage.serializer(),
                InteractionProducer.COOP_MESSAGES_EXCHANGE,
                ExchangeType.SHARDING,
                connection
            ).bind()

        val playerResourceService = PlayerResourceService(equipmentChangeProducer)

        val hostTag = System.getProperty("rabbitHostTag", "develop")
        val threadToHostTag = (Int::toString).andThen(hostTag::plus)

        fun <T> gameEngineResource(interactionConsumer: InteractionConsumer<T>): (String) -> Resource<Unit> = {
            InteractionConsumerFactory.create<T>(
                interactionConsumer,
                it,
                connection
            )
        }

        val coopGameEngineResource: (String) -> Resource<Unit> = gameEngineResource(
            CoopGameEngineService(
                coopStatesDataConnector,
                systemOutputProducer,
                equipmentChangeProducer,
                TravelCoopServiceImpl(systemOutputProducer, playerResourceService)
            )
        )

        (1..gameEngineConfig.numOfThreads).map(threadToHostTag.andThen(coopGameEngineResource))
            .forEach {
                it.bind()
            }

        val tradeGameEngineResource = gameEngineResource(
            TradeGameEngineService(
                tradeStatesDataConnector,
                systemOutputProducer,
                EquipmentTradeServiceImpl(playerResourceService),
                advertisementDataConnector
            )
        )

        (1..gameEngineConfig.numOfThreads).map(threadToHostTag.andThen(tradeGameEngineResource))
            .forEach {
                it.bind()
            }

        val equipmentGameEngineServiceResource = gameEngineResource(
            EquipmentGameEngineService(
                coopInternalMessageProducer,
                systemOutputProducer,
                coopStatesDataConnector
            )
        )

        (1..gameEngineConfig.numOfThreads).map(threadToHostTag.andThen(equipmentGameEngineServiceResource))
            .forEach {
                it.bind()
            }

        gameEngineResource(
            ProductionGameEngineService(
                systemOutputProducer,
                playerResourceService,
            )
        )(hostTag).bind()

        EquipmentChangeQueueService(equipmentChangeProducer, systemOutputProducer).startEquipmentChangeQueueLoop()

        awaitCancellation()
    }
}
