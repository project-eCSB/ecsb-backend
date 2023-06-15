package pl.edu.agh.chat.service

import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.resource
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.ConnectionFactory
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import pl.edu.agh.chat.domain.BetterMessage
import pl.edu.agh.chat.domain.MessageADT
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.rabbit.RabbitConfig
import pl.edu.agh.utils.LoggerDelegate
import java.lang.Thread.sleep
import java.nio.charset.StandardCharsets

class InteractionProducer(private val channel: Channel<BetterMessage<MessageADT.SystemInputMessage>>) {
    companion object {
        private val logger by LoggerDelegate()

        @OptIn(DelicateCoroutinesApi::class)
        fun create(
            rabbitConfig: RabbitConfig
        ): Resource<InteractionProducer> = resource(acquire = {
            val factory = ConnectionFactory()
            factory.isAutomaticRecoveryEnabled = true
            factory.host = rabbitConfig.host
            factory.port = rabbitConfig.port
            val channel = Channel<BetterMessage<MessageADT.SystemInputMessage>>(Channel.UNLIMITED)
            val producerJob = GlobalScope.launch {
                initializeProducer(channel)
            }
            Triple(producerJob, channel, InteractionProducer(channel))
        }, release = { resourceValue, _ ->
            val (producerJob, channel, _) = resourceValue
            channel.cancel()
            producerJob.cancel()
            logger.info("End of InteractionProducer resource")
        }).map { it.third }

        const val exchangeName = "interaction-ex"

        private suspend fun initializeProducer(messageChannel: Channel<BetterMessage<MessageADT.SystemInputMessage>>) {
            val factory = ConnectionFactory()
            factory.isAutomaticRecoveryEnabled = true
            factory.newConnection().use { connection ->
                connection.createChannel().use { channel ->
                    channel.exchangeDeclare(exchangeName, BuiltinExchangeType.FANOUT)
                    logger.info("channel created")
                    while (true) {
                        val message = messageChannel.receive()
                        logger.info("Message is being sent on $exchangeName: $message")
                        try {
                            channel.basicPublish(
                                exchangeName,
                                "interaction",
                                null,
                                Json.encodeToString(
                                    BetterMessage.serializer(MessageADT.SystemInputMessage.serializer()),
                                    message
                                ).toByteArray(StandardCharsets.UTF_8)
                            )
                        } catch (e: Exception) {
                            logger.error("message not sent $message, sleep some time and retry")
                            sleep(2500)
                        }
                    }
                }
            }
        }
    }

    suspend fun sendMessage(gameSessionId: GameSessionId, senderId: PlayerId, message: MessageADT.SystemInputMessage) {
        channel.send(BetterMessage(gameSessionId, senderId, message))
    }
}
