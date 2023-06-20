package pl.edu.agh.coop

import arrow.continuations.SuspendApp
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.resourceScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.KSerializer
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.coop.domain.CoopStates
import pl.edu.agh.coop.domain.GameEngineConfig
import pl.edu.agh.coop.redis.CoopStatesDataConnectorImpl
import pl.edu.agh.coop.service.CoopGameEngineService
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.interaction.domain.InteractionDto
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.utils.ConfigUtils
import pl.edu.agh.utils.DatabaseConnector

fun main(): Unit = SuspendApp {
    val gameEngineConfig = ConfigUtils.getConfigOrThrow<GameEngineConfig>()

    fun <T> createRedisConnector(
        prefix: String,
        tSerializer: KSerializer<T>
    ): Resource<RedisHashMapConnector<GameSessionId, PlayerId, T>> =
        RedisHashMapConnector.createAsResource(
            gameEngineConfig.redis,
            prefix,
            GameSessionId::toName,
            PlayerId.serializer(),
            tSerializer
        )

    resourceScope {
        val redisInteractionStatusConnector = createRedisConnector(
            RedisHashMapConnector.INTERACTION_DATA_PREFIX,
            InteractionDto.serializer()
        ).bind()

        val redisCoopStatesConnector = createRedisConnector(
            RedisHashMapConnector.COOP_STATES_DATA_PREFIX,
            CoopStates.serializer()
        ).bind()

        val coopStatesDataConnector = CoopStatesDataConnectorImpl(redisCoopStatesConnector)

        DatabaseConnector.initDBAsResource().bind()

        val interactionProducer = InteractionProducer.create(
            gameEngineConfig.rabbit,
            ChatMessageADT.SystemInputMessage.serializer(),
            InteractionProducer.INTERACTION_EXCHANGE
        ).bind()

        InteractionConsumer.create<CoopInternalMessages>(
            gameEngineConfig.rabbit,
            CoopGameEngineService(coopStatesDataConnector, redisInteractionStatusConnector, interactionProducer),
            System.getProperty("rabbitHostTag", "develop")
        ).bind()

        awaitCancellation()
    }
}
