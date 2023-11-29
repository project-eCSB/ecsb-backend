package pl.edu.agh.interaction.service

import arrow.core.partially1
import arrow.core.raise.either
import arrow.core.toNonEmptySetOrNone
import arrow.core.toOption
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.ChatMessageADT.SystemOutputMessage.MulticastMessage
import pl.edu.agh.chat.domain.CoopMessages
import pl.edu.agh.chat.domain.TimeMessages
import pl.edu.agh.chat.domain.TradeMessages
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.GameSessionDao
import pl.edu.agh.interaction.domain.Message
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.moving.domain.PlayerPosition
import pl.edu.agh.redis.RedisJsonConnector
import pl.edu.agh.utils.ExchangeType
import pl.edu.agh.utils.Transactor
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.time.Duration

class InteractionMessagePasser(
    sessionStorage: SessionStorage<WebSocketSession>,
    private val redisJsonConnector: RedisJsonConnector<PlayerId, PlayerPosition>
) : MessagePasser<Message<ChatMessageADT>>(sessionStorage, Message.serializer(ChatMessageADT.serializer())),
    InteractionConsumer<ChatMessageADT.SystemOutputMessage> {

    override val tSerializer: KSerializer<ChatMessageADT.SystemOutputMessage> =
        ChatMessageADT.SystemOutputMessage.serializer()

    override fun consumeQueueName(hostTag: String): String = "interaction-queue-$hostTag"
    override fun exchangeName(): String = InteractionProducer.INTERACTION_EXCHANGE
    override fun exchangeType(): ExchangeType = ExchangeType.FANOUT
    override fun autoDelete(): Boolean = false

    private suspend fun sendAutoCancellableMessages(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        sentAt: LocalDateTime,
        message: ChatMessageADT.SystemOutputMessage.AutoCancelNotification,
        timeout: Duration
    ) {
        logger.info("Sending auto-cancelling message $message")
        sendToNearby(
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
        logger.trace("Received message {} from {} {} at {}", message, gameSessionId, senderId, sentAt)
        val broadcast = ::broadcast.partially1(gameSessionId)
        val unicast = ::unicast.partially1(gameSessionId)
        val sendToNearby = ::sendToNearby.partially1(gameSessionId)
        when (message) {
            is MulticastMessage -> sendToNearby(
                message.senderId,
                Message(message.senderId, message, sentAt)
            )

            is TradeMessages.TradeSystemOutputMessage.NotificationTradeStart -> sendToNearby(
                senderId,
                Message(senderId, message, sentAt)
            )

            is TradeMessages.TradeSystemOutputMessage.NotificationTradeEnd -> sendToNearby(
                senderId,
                Message(senderId, message, sentAt)
            )

            is TradeMessages.TradeSystemOutputMessage.CancelTradeAtAnyStage -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is TradeMessages.TradeSystemOutputMessage.TradeAckMessage -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is TradeMessages.TradeSystemOutputMessage.TradeFinishMessage -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.UserWarningMessage -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is TradeMessages.TradeSystemOutputMessage.ProposeTradeMessage -> unicast(
                senderId,
                message.proposalReceiverId,
                Message(senderId, message, sentAt)
            )

            is TradeMessages.TradeSystemOutputMessage.TradeBidMessage -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is TradeMessages.TradeSystemOutputMessage.AdvertiseBuy -> broadcast(
                senderId,
                Message(senderId, message, sentAt)
            )

            is TradeMessages.TradeSystemOutputMessage.AdvertiseSell -> broadcast(
                senderId,
                Message(senderId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.TravelChoosing.TravelChoosingStart -> sendToNearby(
                senderId,
                Message(senderId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.TravelChoosing.TravelChoosingStop -> sendToNearby(
                senderId,
                Message(senderId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.AutoCancelNotification.TravelStart -> GlobalScope.launch {
                sendAutoCancellableMessages(
                    gameSessionId,
                    senderId,
                    sentAt,
                    message,
                    message.timeout
                )
            }

            is ChatMessageADT.SystemOutputMessage.WorkshopMessages.WorkshopChoosingStart -> sendToNearby(
                senderId,
                Message(senderId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.WorkshopMessages.WorkshopChoosingStop -> sendToNearby(
                senderId,
                Message(senderId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.WorkshopMessages.WorkshopAccept -> unicast(
                senderId,
                senderId,
                Message(senderId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.WorkshopMessages.WorkshopDeny -> unicast(
                senderId,
                senderId,
                Message(senderId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.AutoCancelNotification.ProductionStart -> GlobalScope.launch {
                sendAutoCancellableMessages(
                    gameSessionId,
                    senderId,
                    sentAt,
                    message,
                    message.timeout
                )
            }

            is CoopMessages.CoopSystemOutputMessage.StartPlanningSystem -> unicast(
                senderId,
                message.ownerId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.StartAdvertisingCoop -> broadcast(
                gameSessionId,
                senderId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.StopAdvertisingCoop -> broadcast(
                gameSessionId,
                senderId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.NotificationCoopStart -> sendToNearby(
                senderId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.NotificationCoopStop -> sendToNearby(
                senderId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.CancelCoopAtAnyStage -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.SimpleJoinPlanning -> unicast(
                senderId,
                message.ownerId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.GatheringJoinPlanning -> unicast(
                senderId,
                message.ownerId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.ProposeOwnTravel -> unicast(
                senderId,
                message.guestId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.ResourceNegotiationStart -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.ResourceNegotiationBid -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.ResourceNegotiationFinish -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.CoopResourceChange -> message.equipments.forEach { (user, _) ->
                unicast(
                    senderId,
                    user,
                    Message(senderId, message, sentAt)
                )
            }

            is CoopMessages.CoopSystemOutputMessage.GoToGateAndTravel -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.WaitForCoopEnd -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.TravelAccept -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.TravelDeny -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.CoopFinish -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.CancelPlanningAtAnyStage -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.PlayerResourceChanged -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            TimeMessages.TimeSystemOutputMessage.GameTimeEnd -> broadcast(senderId, Message(senderId, message, sentAt))

            is TimeMessages.TimeSystemOutputMessage.GameTimeRemaining -> broadcast(
                senderId,
                Message(senderId, message, sentAt)
            )

            is TimeMessages.TimeSystemOutputMessage.GameTimeSyncResponse -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.CancelMessages -> logger.error(
                "This message should not be present here $message"
            )

            is TimeMessages.TimeSystemOutputMessage.SessionPlayersTokensRefresh -> message.tokens.forEach { (playerId, tokens) ->
                unicast(
                    senderId,
                    playerId,
                    Message(
                        senderId,
                        TimeMessages.TimeSystemOutputMessage.PlayerTokensRefresh(playerId, tokens),
                        sentAt
                    )
                )
            }

            is TimeMessages.TimeSystemOutputMessage.PlayerTokensRefresh -> unicast(
                senderId,
                message.playerId,
                Message(senderId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.QueueEquipmentChangePerformed -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.AdvertisingSync -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message)
            )

            is TradeMessages.TradeSystemOutputMessage.TradeSyncMessage -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message)
            )

            is CoopMessages.CoopSystemOutputMessage.CancelNegotiationAtAnyStage -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message)
            )
        }
    }

    private suspend fun sendToNearby(gameSessionId: GameSessionId, playerId: PlayerId, message: Message<ChatMessageADT>) {
        either {
            val playerPositions = redisJsonConnector.getAll(gameSessionId)
            val interactionRadius = Transactor.dbQuery {
                GameSessionDao.getGameSessionRadius(gameSessionId)
            }.toEither { "Game session $gameSessionId not found" }.bind()
            val currentUserPosition =
                playerPositions[playerId].toOption().toEither { "Current position not found" }.bind()

            playerPositions.filter { (_, position) ->
                position.coords.isInRange(currentUserPosition.coords, interactionRadius.value)
            }.map { (playerId, _) -> playerId }.filterNot { it == playerId }.toNonEmptySetOrNone()
                .toEither { "No players found to send message" }.bind()
        }.fold(ifLeft = { err ->
            logger.info("Couldn't send message because $err")
        }, ifRight = { nearbyPlayers ->
            multicast(
                gameSessionId = gameSessionId,
                fromId = message.senderId,
                toIds = nearbyPlayers,
                message = message
            )
        })
    }
}
