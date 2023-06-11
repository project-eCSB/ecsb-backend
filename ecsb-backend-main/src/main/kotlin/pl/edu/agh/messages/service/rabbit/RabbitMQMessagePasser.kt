package pl.edu.agh.messages.service.rabbit

import arrow.core.*
import arrow.core.raise.option
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.resource
import com.rabbitmq.client.ConnectionFactory
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.messages.domain.MessageWrapper
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.utils.LoggerDelegate
import java.nio.charset.StandardCharsets
import java.util.*

class RabbitMQMessagePasser<T> private constructor(channel: Channel<MessageWrapper<T>>) : MessagePasser<T>(channel) {

    class RabbitMQMessagePasserConsumer<T>(
        private val sessionStorage: SessionStorage<WebSocketSession>,
        private val kSerializer: KSerializer<T>,
        private val queueName: String
    ) {

        private suspend fun callback(messageWrapper: MessageWrapper<T>) {
            when (messageWrapper.sendTo) {
                None -> broadcast(messageWrapper.gameSessionId, messageWrapper.senderId, messageWrapper.message)
                is Some -> multicast(
                    messageWrapper.gameSessionId,
                    messageWrapper.senderId,
                    messageWrapper.sendTo.value,
                    messageWrapper.message
                )
            }
        }

        fun consume(factory: ConnectionFactory) {
            logger.info("Start consuming messages")
            factory.newConnection().use { connection ->
                connection.createChannel().use { channel ->
                    val consumerTag = UUID.randomUUID().toString().substring(0, 7)
                    logger.info("[$consumerTag] Waiting for messages...")
                    while (true) {
                        try {
                            channel.basicConsume(
                                queueName,
                                true,
                                JsonRabbitConsumer<MessageWrapper<T>>(
                                    MessageWrapper.serializer(kSerializer),
                                    channel,
                                    ::callback
                                )
                            )
                        } catch (e: Exception) {
                            logger.error("Basic consume failed!, keep running after sleep")
                            Thread.sleep(2500)
                        }
                    }
                }
            }
        }

        private suspend fun broadcast(gameSessionId: GameSessionId, senderId: PlayerId, message: T) {
            logger.info("[Sending] Broadcasting message $message from $senderId")
            sessionStorage.getSessions(gameSessionId)?.forEach { (user, session) ->
                if (user != senderId) {
                    session.outgoing.send(Frame.Text(Json.encodeToString(kSerializer, message)))
                }
            }
        }

        private suspend fun multicast(
            gameSessionId: GameSessionId,
            fromId: PlayerId,
            toIds: NonEmptySet<PlayerId>,
            message: T
        ) {
            logger.info("[Sending] Multicasting message $message from $fromId to $toIds")
            option {
                val sessions = Option.fromNullable(sessionStorage.getSessions(gameSessionId)).bind()
                toIds.forEach { playerId ->
                    sessions[playerId]?.outgoing?.send(
                        Frame.Text(Json.encodeToString(kSerializer, message))
                    )
                }
            }.getOrElse { logger.warn("Game session $gameSessionId not found") }
        }
    }

    companion object {
        private val logger by LoggerDelegate()

        @OptIn(DelicateCoroutinesApi::class)
        fun <T> create(
            sessionStorage: SessionStorage<WebSocketSession>,
            kSerializer: KSerializer<T>,
            queueName: String
        ): Resource<RabbitMQMessagePasser<T>> = resource(acquire = {
            val factory = ConnectionFactory()
            factory.isAutomaticRecoveryEnabled = true
            factory.host = "localhost"
            factory.port = 5672
            val channel = Channel<MessageWrapper<T>>(Channel.UNLIMITED)
            val consumerJob = GlobalScope.launch {
                val simpleConsumer = RabbitMQMessagePasserConsumer<T>(sessionStorage, kSerializer, queueName)
                simpleConsumer.consume(factory)
            }
            val producerJob = GlobalScope.launch {
                initProducer(factory, queueName, channel, kSerializer)
            }

            Tuple4(RabbitMQMessagePasser<T>(channel), consumerJob, channel, producerJob)
        }, release = { resourceValue, _ ->
                val (_, consumerJob, channel, producerJob) = resourceValue
                channel.cancel()
                producerJob.cancel()
                consumerJob.cancel()
                logger.info("End of RabbitMQMessagePasser resource")
            }).map { it.first }

        private suspend fun <T> initProducer(
            factory: ConnectionFactory,
            queueName: String,
            messageChannel: Channel<MessageWrapper<T>>,
            kSerializer: KSerializer<T>
        ) {
            factory.newConnection().use { connection ->
                connection.createChannel().use { channel ->
                    channel.queueDeclare(queueName, true, false, false, null)
                    logger.info("channel created")
                    while (true) {
                        val message = messageChannel.receive()
                        logger.info("Message is being sent on $queueName: $message")
                        try {
                            channel.basicPublish(
                                "",
                                queueName,
                                null,
                                Json.encodeToString(MessageWrapper.serializer(kSerializer), message)
                                    .toByteArray(StandardCharsets.UTF_8)
                            )
                        } catch (e: Exception) {
                            logger.error("message not sent $message, sleep some time and retry")
                            Thread.sleep(2500)
                        }
                    }
                }
            }
        }
    }
}
