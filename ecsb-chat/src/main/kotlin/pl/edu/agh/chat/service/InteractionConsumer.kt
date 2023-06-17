package pl.edu.agh.chat.service

import arrow.core.raise.either
import arrow.core.toNonEmptySetOrNone
import arrow.core.toOption
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.resource
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Channel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.edu.agh.chat.domain.BetterMessage
import pl.edu.agh.chat.domain.Message
import pl.edu.agh.chat.domain.MessageADT
import pl.edu.agh.chat.domain.MessageADT.SystemInputMessage.MulticastMessage
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.rabbit.JsonRabbitConsumer
import pl.edu.agh.rabbit.RabbitConfig
import pl.edu.agh.rabbit.RabbitFactory
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.utils.LoggerDelegate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.time.Duration

class InteractionConsumer() {

    companion object {
        private fun consumeQueueName(hostTag: String): String = "interaction-queue-$hostTag"

        private val logger by LoggerDelegate()

        fun create(
            rabbitConfig: RabbitConfig,
            messagePasser: MessagePasser<Message>,
            redisHashMapConnector: RedisHashMapConnector<GameSessionId, PlayerId, PlayerPosition>,
            hostTag: String
        ): Resource<Unit> = resource {
            val channel = RabbitFactory.getChannelResource(rabbitConfig).bind()

            val simpleConsumer = RabbitMQMessagePasserConsumer(
                messagePasser,
                redisHashMapConnector,
                consumeQueueName(hostTag)
            )
            simpleConsumer.consume(channel)
        }

        class RabbitMQMessagePasserConsumer(
            private val messagePasser: MessagePasser<Message>,
            private val redisHashMapConnector: RedisHashMapConnector<GameSessionId, PlayerId, PlayerPosition>,
            private val queueName: String
        ) {

            fun consume(channel: Channel) {
                logger.info("Start consuming messages")
                channel.exchangeDeclare(InteractionProducer.exchangeName, BuiltinExchangeType.FANOUT)
                channel.queueDeclare(queueName, true, false, false, mapOf())
                channel.queueBind(queueName, InteractionProducer.exchangeName, "")
                val consumerTag = UUID.randomUUID().toString().substring(0, 7)
                channel.basicConsume(
                    queueName,
                    true,
                    JsonRabbitConsumer<BetterMessage<MessageADT.SystemInputMessage>>(
                        BetterMessage.serializer(MessageADT.SystemInputMessage.serializer()),
                        channel,
                        ::callback
                    )
                )
                logger.info("[$consumerTag] Waiting for messages...")
            }

            private suspend fun sendAutoCancellableMessages(
                gameSessionId: GameSessionId,
                playerId: PlayerId,
                sentAt: LocalDateTime,
                message: MessageADT.SystemInputMessage.AutoCancelNotification,
                timeout: Duration
            ) {
                logger.info("Sending autocancelling message $message")
                messagePasser.broadcast(
                    gameSessionId,
                    playerId,
                    Message(
                        playerId,
                        message,
                        sentAt
                    )
                )
                val milliseconds = sentAt.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
                val currentMillis = LocalDateTime.now().atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
                val leftMillis =
                    (milliseconds + timeout.inWholeMilliseconds) - currentMillis
                logger.info("[Send cancel message] Left $leftMillis from ${timeout.inWholeMilliseconds} to send $message")
                delay(timeout.inWholeMilliseconds)
                messagePasser.broadcast(
                    gameSessionId,
                    playerId,
                    Message(
                        playerId,
                        message.getCanceledMessage()
                    )
                )
            }

            @OptIn(DelicateCoroutinesApi::class)
            private suspend fun callback(message: BetterMessage<MessageADT.SystemInputMessage>) {
                logger.info("Received message $message")
                when (message.message) {
                    is MessageADT.SystemInputMessage.TradeEnd -> messagePasser.broadcast(
                        message.gameSessionId,
                        message.message.playerId,
                        Message(
                            message.message.playerId,
                            message.message,
                            message.sentAt
                        )
                    )

                    is MessageADT.SystemInputMessage.TradeStart -> {
                        messagePasser.broadcast(
                            message.gameSessionId,
                            message.message.playerId,
                            Message(
                                message.message.playerId,
                                message.message,
                                message.sentAt
                            )
                        )
                    }

                    is MulticastMessage -> sendToNearby(
                        message.gameSessionId,
                        message.message.senderId,
                        Message(
                            message.message.senderId,
                            message.message,
                            message.sentAt
                        )
                    )

                    is MessageADT.SystemInputMessage.AutoCancelNotification.TravelStart -> GlobalScope.launch {
                        sendAutoCancellableMessages(
                            message.gameSessionId,
                            message.message.playerId,
                            message.sentAt,
                            message.message,
                            message.message.timeout
                        )
                    }

                    is MessageADT.SystemInputMessage.AutoCancelNotification.ProductionStart -> GlobalScope.launch {
                        sendAutoCancellableMessages(
                            message.gameSessionId,
                            message.message.playerId,
                            message.sentAt,
                            message.message,
                            message.message.timeout
                        )
                    }

                    is MessageADT.SystemInputMessage.WorkshopNotification.WorkshopChoosingStart -> messagePasser.broadcast(
                        message.gameSessionId,
                        message.message.playerId,
                        Message(
                            message.message.playerId,
                            message.message,
                            message.sentAt
                        )
                    )

                    is MessageADT.SystemInputMessage.WorkshopNotification.WorkshopChoosingStop -> messagePasser.broadcast(
                        message.gameSessionId,
                        message.message.playerId,
                        Message(
                            message.message.playerId,
                            message.message,
                            message.sentAt
                        )
                    )

                    is MessageADT.SystemInputMessage.CancelMessages -> logger.error("This message should not be present here $message")
                }
            }

            private suspend fun sendToNearby(gameSessionId: GameSessionId, playerId: PlayerId, message: Message) {
                either {
                    val playerPositions = redisHashMapConnector.getAll(gameSessionId)

                    val currentUserPosition =
                        playerPositions[playerId].toOption().toEither { "Current position not found" }.bind()

                    playerPositions.filter { (_, position) ->
                        position.coords.isInRange(currentUserPosition.coords, playersRange)
                    }.map { (playerId, _) -> playerId }.filterNot { it == playerId }.toNonEmptySetOrNone()
                        .toEither { "No players found to send message" }.bind()
                }.fold(ifLeft = { err ->
                    logger.warn("Couldn't send message because $err")
                }, ifRight = { nearbyPlayers ->
                        messagePasser.multicast(
                            gameSessionId = gameSessionId,
                            fromId = message.senderId,
                            toIds = nearbyPlayers,
                            message = message
                        )
                    })
            }
        }

        private const val playersRange: Int = 7
    }
}
