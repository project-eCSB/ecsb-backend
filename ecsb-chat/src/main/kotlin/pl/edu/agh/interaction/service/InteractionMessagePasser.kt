package pl.edu.agh.interaction.service

import arrow.core.partially1
import arrow.core.raise.either
import arrow.core.toNonEmptySetOrNone
import arrow.core.toOption
import com.rabbitmq.client.Channel
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.ChatMessageADT.SystemOutputMessage.MulticastMessage
import pl.edu.agh.chat.domain.CoopMessages
import pl.edu.agh.chat.domain.Message
import pl.edu.agh.chat.domain.TradeMessages
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.redis.RedisJsonConnector
import pl.edu.agh.utils.ExchangeType
import pl.edu.agh.utils.LoggerDelegate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.time.Duration

class InteractionMessagePasser(
    sessionStorage: SessionStorage<WebSocketSession>,
    private val redisJsonConnector: RedisJsonConnector<PlayerId, PlayerPosition>
) : MessagePasser<Message>(sessionStorage, Message.serializer()),
    InteractionConsumer<ChatMessageADT.SystemOutputMessage> {

    override val tSerializer: KSerializer<ChatMessageADT.SystemOutputMessage> =
        ChatMessageADT.SystemOutputMessage.serializer()

    override fun consumeQueueName(hostTag: String): String = "interaction-queue-$hostTag"
    override fun exchangeName(): String = InteractionProducer.INTERACTION_EXCHANGE
    override fun bindQueue(channel: Channel, queueName: String) {
        channel.exchangeDeclare(exchangeName(), ExchangeType.FANOUT.value)
        channel.queueDeclare(queueName, true, false, false, mapOf())
        channel.queueBind(queueName, exchangeName(), "")
    }

    private suspend fun sendAutoCancellableMessages(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        sentAt: LocalDateTime,
        message: ChatMessageADT.SystemOutputMessage.AutoCancelNotification,
        timeout: Duration
    ) {
        logger.info("Sending auto-cancelling message $message")
        broadcast(
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
        broadcast(
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
        message: ChatMessageADT.SystemOutputMessage
    ) {
        logger.info("Received message $message from $gameSessionId $senderId at $sentAt")
        val broadcast = ::broadcast.partially1(gameSessionId)
        val unicast = ::unicast.partially1(gameSessionId)
        when (message) {
            is MulticastMessage -> sendToNearby(
                gameSessionId,
                message.senderId,
                Message(
                    message.senderId,
                    message,
                    sentAt
                )
            )

            is TradeMessages.TradeSystemOutputMessage.SearchingForTrade -> sendToNearby(
                gameSessionId,
                message.playerId,
                Message(message.playerId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.NotificationTradeStart -> {
                broadcast(
                    message.playerId,
                    Message(
                        message.playerId,
                        message,
                        sentAt
                    )
                )
            }

            is ChatMessageADT.SystemOutputMessage.NotificationTradeEnd ->
                broadcast(
                    message.playerId,
                    Message(
                        message.playerId,
                        message,
                        sentAt
                    )
                )

            TradeMessages.TradeSystemOutputMessage.CancelTradeAtAnyStage -> broadcast(
                senderId,
                Message(senderId, message, sentAt)
            )

            is TradeMessages.TradeSystemOutputMessage.TradeAckMessage -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message)
            )

            is TradeMessages.TradeSystemOutputMessage.TradeFinishMessage -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message)
            )

            is ChatMessageADT.SystemOutputMessage.UserBusyMessage -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message)
            )

            is TradeMessages.TradeSystemOutputMessage.ProposeTradeMessage -> unicast(
                senderId,
                message.proposalReceiverId,
                Message(senderId, message)
            )

            is TradeMessages.TradeSystemOutputMessage.TradeBidMessage -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message)
            )

            is ChatMessageADT.SystemOutputMessage.TravelNotification.TravelChoosingStart -> broadcast(
                message.playerId,
                Message(message.playerId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.TravelNotification.TravelChoosingStop -> broadcast(
                message.playerId,
                Message(message.playerId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.AutoCancelNotification.TravelStart -> GlobalScope.launch {
                sendAutoCancellableMessages(
                    gameSessionId,
                    message.playerId,
                    sentAt,
                    message,
                    message.timeout
                )
            }

            is ChatMessageADT.SystemOutputMessage.WorkshopNotification.WorkshopChoosingStart -> broadcast(
                message.playerId,
                Message(
                    message.playerId,
                    message,
                    sentAt
                )
            )

            is ChatMessageADT.SystemOutputMessage.WorkshopNotification.WorkshopChoosingStop -> broadcast(
                message.playerId,
                Message(
                    message.playerId,
                    message,
                    sentAt
                )
            )

            is ChatMessageADT.SystemOutputMessage.AutoCancelNotification.ProductionStart -> GlobalScope.launch {
                sendAutoCancellableMessages(
                    gameSessionId,
                    message.playerId,
                    sentAt,
                    message,
                    message.timeout
                )
            }

            is CoopMessages.CoopSystemOutputMessage.SearchingForCoop -> sendToNearby(
                gameSessionId,
                message.playerId,
                Message(message.playerId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.NotificationCoopStart -> broadcast(
                message.playerId,
                Message(message.playerId, message, sentAt)
            )

            CoopMessages.CoopSystemOutputMessage.CancelCoopAtAnyStage -> broadcast(
                senderId,
                Message(senderId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.NotificationCoopStop -> broadcast(
                message.playerId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.ResourceDecideAck -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.ResourceDecide -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.CityDecide -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.CityDecideAck -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.ProposeCoop -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.ProposeCoopAck -> unicast(
                senderId,
                message.proposalSenderId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.GoToGateAndTravel -> unicast(
                senderId,
                senderId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.WaitForCoopEnd -> unicast(
                senderId,
                senderId,
                Message(senderId, message, sentAt)
            )

            CoopMessages.CoopSystemOutputMessage.RenegotiateCityRequest -> unicast(
                senderId,
                senderId,
                Message(senderId, message, sentAt)
            )

            CoopMessages.CoopSystemOutputMessage.RenegotiateResourcesRequest -> unicast(
                senderId,
                senderId,
                Message(senderId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.CancelMessages -> logger.error(
                "This message should not be present here $message"
            )
        }
    }

    private suspend fun sendToNearby(gameSessionId: GameSessionId, playerId: PlayerId, message: Message) {
        either {
            val playerPositions = redisJsonConnector.getAll(gameSessionId)

            val currentUserPosition =
                playerPositions[playerId].toOption().toEither { "Current position not found" }.bind()

            playerPositions.filter { (_, position) ->
                position.coords.isInRange(currentUserPosition.coords, playersRange)
            }.map { (playerId, _) -> playerId }.filterNot { it == playerId }.toNonEmptySetOrNone()
                .toEither { "No players found to send message" }.bind()
        }.fold(ifLeft = { err ->
            logger.warn("Couldn't send message because $err")
        }, ifRight = { nearbyPlayers ->
            multicast(
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
