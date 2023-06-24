package pl.edu.agh.interaction.service

import arrow.core.partially1
import arrow.core.raise.either
import arrow.core.toNonEmptySetOrNone
import arrow.core.toOption
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Channel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.ChatMessageADT.SystemInputMessage.MulticastMessage
import pl.edu.agh.chat.domain.CoopMessages
import pl.edu.agh.chat.domain.Message
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.utils.LoggerDelegate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.time.Duration

class InteractionMessagePasser(
    private val messagePasser: MessagePasser<Message>,
    private val redisHashMapConnector: RedisHashMapConnector<GameSessionId, PlayerId, PlayerPosition>
) : InteractionConsumerCallback<ChatMessageADT.SystemInputMessage> {
    private val logger by LoggerDelegate()

    override val tSerializer: KSerializer<ChatMessageADT.SystemInputMessage> =
        ChatMessageADT.SystemInputMessage.serializer()

    override fun consumeQueueName(hostTag: String): String = "interaction-queue-$hostTag"
    override fun exchangeName(): String = InteractionProducer.INTERACTION_EXCHANGE
    override fun bindQueues(channel: Channel, queueName: String) {
        channel.exchangeDeclare(exchangeName(), BuiltinExchangeType.FANOUT)
        channel.queueDeclare(queueName, true, false, false, mapOf())
        channel.queueBind(queueName, exchangeName(), "")
    }

    private suspend fun sendAutoCancellableMessages(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        sentAt: LocalDateTime,
        message: ChatMessageADT.SystemInputMessage.AutoCancelNotification,
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
    override suspend fun callback(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        sentAt: LocalDateTime,
        message: ChatMessageADT.SystemInputMessage
    ) {
        logger.info("Received message $message from $gameSessionId $senderId at $sentAt")
        val broadcast = messagePasser::broadcast.partially1(gameSessionId)
        val unicast = messagePasser::unicast.partially1(gameSessionId)
        when (message) {
            is ChatMessageADT.SystemInputMessage.TradeEnd ->
                broadcast(
                    message.playerId,
                    Message(
                        message.playerId,
                        message,
                        sentAt
                    )
                )

            is ChatMessageADT.SystemInputMessage.TradeStart -> {
                broadcast(
                    message.playerId,
                    Message(
                        message.playerId,
                        message,
                        sentAt
                    )
                )
            }

            is MulticastMessage -> sendToNearby(
                gameSessionId,
                message.senderId,
                Message(
                    message.senderId,
                    message,
                    sentAt
                )
            )

            is ChatMessageADT.SystemInputMessage.AutoCancelNotification.TravelStart -> GlobalScope.launch {
                sendAutoCancellableMessages(
                    gameSessionId,
                    message.playerId,
                    sentAt,
                    message,
                    message.timeout
                )
            }

            is ChatMessageADT.SystemInputMessage.AutoCancelNotification.ProductionStart -> GlobalScope.launch {
                sendAutoCancellableMessages(
                    gameSessionId,
                    message.playerId,
                    sentAt,
                    message,
                    message.timeout
                )
            }

            is ChatMessageADT.SystemInputMessage.WorkshopNotification.WorkshopChoosingStart -> broadcast(
                message.playerId,
                Message(
                    message.playerId,
                    message,
                    sentAt
                )
            )

            is ChatMessageADT.SystemInputMessage.WorkshopNotification.WorkshopChoosingStop -> broadcast(
                message.playerId,
                Message(
                    message.playerId,
                    message,
                    sentAt
                )
            )

            is CoopMessages.CoopSystemInputMessage.SearchingForCoop -> sendToNearby(
                gameSessionId,
                message.playerId,
                Message(message.playerId, message, sentAt)
            )

            is ChatMessageADT.SystemInputMessage.TravelNotification.TravelChoosingStart -> messagePasser.broadcast(
                gameSessionId,
                message.playerId,
                Message(message.playerId, message, sentAt)
            )

            is ChatMessageADT.SystemInputMessage.TravelNotification.TravelChoosingStop -> messagePasser.broadcast(
                gameSessionId,
                message.playerId,
                Message(message.playerId, message, sentAt)
            )

            is ChatMessageADT.SystemInputMessage.NotificationCoopStart -> broadcast(
                message.playerId,
                Message(message.playerId, message, sentAt)
            )

            CoopMessages.CoopSystemInputMessage.CancelCoopAtAnyStage -> broadcast(
                senderId,
                Message(senderId, message, sentAt)
            )

            is ChatMessageADT.SystemInputMessage.NotificationCoopStop -> broadcast(
                message.playerId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemInputMessage.ResourceDecideAck -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemInputMessage.ResourceDecide -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is ChatMessageADT.SystemInputMessage.CancelMessages -> logger.error("This message should not be present here $message")
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

    companion object {
        private const val playersRange: Int = 7
    }
}
