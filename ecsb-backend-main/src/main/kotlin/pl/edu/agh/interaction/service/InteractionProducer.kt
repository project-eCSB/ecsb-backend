package pl.edu.agh.interaction.service

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.release
import arrow.fx.coroutines.resource
import com.rabbitmq.client.BuiltinExchangeType
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.interaction.domain.BetterMessage
import pl.edu.agh.rabbit.RabbitConfig
import pl.edu.agh.rabbit.RabbitFactory
import pl.edu.agh.utils.LoggerDelegate
import java.lang.Thread.sleep
import java.nio.charset.StandardCharsets

class InteractionProducer<T>(private val channel: Channel<BetterMessage<T>>) {
    companion object {
        private val logger by LoggerDelegate()

        @OptIn(DelicateCoroutinesApi::class)
        fun <T> create(
            rabbitConfig: RabbitConfig,
            tSerializer: KSerializer<T>,
            exchangeName: String
        ): Resource<InteractionProducer<T>> = (
            resource {
                val channel = Channel<BetterMessage<T>>(Channel.UNLIMITED)
                val rabbitMQChannel = RabbitFactory.getChannelResource(rabbitConfig).bind()
                val producerJob = GlobalScope.launch {
                    initializeProducer(rabbitMQChannel, channel, tSerializer, exchangeName)
                }
                Triple(producerJob, channel, InteractionProducer(channel))
            } release { resourceValue ->
                val (producerJob, channel, _) = resourceValue
                channel.cancel()
                producerJob.cancel()
                logger.info("End of InteractionProducer resource")
            }
            ).map { it.third }

        private suspend fun <T> initializeProducer(
            rabbitMQChannel: com.rabbitmq.client.Channel,
            messageChannel: Channel<BetterMessage<T>>,
            tSerializer: KSerializer<T>,
            exchangeName: String
        ) {
            rabbitMQChannel.exchangeDeclare(exchangeName, BuiltinExchangeType.FANOUT)
            rabbitMQChannel.exchangeBind(exchangeName, GAME_EXCHANGE, "$exchangeName.*")
            logger.info("channel created")
            while (true) {
                val message = messageChannel.receive()
                logger.info("Message is being sent on $exchangeName: $message")
                try {
                    rabbitMQChannel.basicPublish(
                        MAIN_EXCHANGE,
                        "$exchangeName.${message.gameSessionId.value}",
                        null,
                        Json.encodeToString(
                            BetterMessage.serializer(tSerializer),
                            message
                        ).toByteArray(StandardCharsets.UTF_8)
                    )
                } catch (e: Exception) {
                    logger.error("message not sent $message, sleep some time and retry $e", e)
                    sleep(2500)
                }
            }
        }

        const val INTERACTION_EXCHANGE = "interaction-ex"
        const val COOP_MESSAGES_EXCHANGE = "coop-ex"
        const val TRADE_MESSAGES_EXCHANGE = "trade-ex"
        const val MAIN_EXCHANGE = "main-ex"
        const val GAME_EXCHANGE = "game-ex"
    }

    suspend fun sendMessage(gameSessionId: GameSessionId, senderId: PlayerId, message: T) {
        channel.send(BetterMessage(gameSessionId, senderId, message))
    }
}
