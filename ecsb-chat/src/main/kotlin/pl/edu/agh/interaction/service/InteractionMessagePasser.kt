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
import pl.edu.agh.chat.domain.*
import pl.edu.agh.chat.domain.ChatMessageADT.SystemOutputMessage.MulticastMessage
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerIdConst
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.game.dao.GameSessionDao
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.redis.RedisJsonConnector
import pl.edu.agh.utils.ExchangeType
import pl.edu.agh.utils.Transactor
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
        logger.trace("Received message $message from $gameSessionId $senderId at $sentAt")
        val broadcast = ::broadcast.partially1(gameSessionId)
        val unicast = ::unicast.partially1(gameSessionId)
        val sendToNearby = ::sendToNearby.partially1(gameSessionId)
        when (message) {
            is MulticastMessage -> sendToNearby(
                message.senderId,
                Message(
                    message.senderId,
                    message,
                    sentAt
                )
            )

            is TradeMessages.TradeSystemOutputMessage.NotificationTradeStart -> sendToNearby(
                message.playerId,
                Message(
                    message.playerId,
                    message,
                    sentAt
                )
            )

            is TradeMessages.TradeSystemOutputMessage.NotificationTradeEnd -> sendToNearby(
                message.playerId,
                Message(
                    message.playerId,
                    message,
                    sentAt
                )
            )

            is TradeMessages.TradeSystemOutputMessage.CancelTradeAtAnyStage -> unicast(
                senderId,
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

            is ChatMessageADT.SystemOutputMessage.UserWarningMessage -> unicast(
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

            is TradeMessages.TradeSystemOutputMessage.AdvertiseBuy -> broadcast(
                message.playerId,
                Message(
                    message.playerId,
                    message,
                    sentAt
                )
            )

            is TradeMessages.TradeSystemOutputMessage.AdvertiseSell -> broadcast(
                message.playerId,
                Message(
                    message.playerId,
                    message,
                    sentAt
                )
            )

            is ChatMessageADT.SystemOutputMessage.TravelChoosing.TravelChoosingStart -> sendToNearby(
                message.playerId,
                Message(message.playerId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.TravelChoosing.TravelChoosingStop -> sendToNearby(
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

            is ChatMessageADT.SystemOutputMessage.WorkshopMessages.WorkshopChoosingStart -> sendToNearby(
                message.playerId,
                Message(
                    message.playerId,
                    message,
                    sentAt
                )
            )

            is ChatMessageADT.SystemOutputMessage.WorkshopMessages.WorkshopChoosingStop -> sendToNearby(
                message.playerId,
                Message(
                    message.playerId,
                    message,
                    sentAt
                )
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
                    message.playerId,
                    sentAt,
                    message,
                    message.timeout
                )
            }

            is CoopMessages.CoopSystemOutputMessage.StartPlanningSystem -> unicast(
                PlayerIdConst.ECSB_COOP_PLAYER_ID,
                senderId,
                Message(PlayerIdConst.ECSB_COOP_PLAYER_ID, message)
            )

            is CoopMessages.CoopSystemOutputMessage.AdvertiseCompanySearching -> broadcast(
                gameSessionId,
                message.ownerId,
                Message(message.ownerId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.StopCompanySearching -> broadcast(
                gameSessionId,
                message.ownerId,
                Message(message.ownerId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.NotificationCoopStart -> sendToNearby(
                message.playerId,
                Message(message.playerId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.NotificationCoopStop -> sendToNearby(
                message.playerId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.CancelCoopAtAnyStage -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.JoinPlanning -> unicast(
                senderId,
                message.ownerId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.ProposeCompany -> unicast(
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

            CoopMessages.CoopSystemOutputMessage.ResourceNegotiationFinish -> unicast(
                senderId,
                senderId,
                Message(senderId, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.ResourceChange -> message.equipments.forEach { (user, _) ->
                unicast(senderId, user, Message(senderId, message, sentAt))
            }

            is CoopMessages.CoopSystemOutputMessage.GoToGateAndTravel -> unicast(
                PlayerIdConst.ECSB_COOP_PLAYER_ID,
                senderId,
                Message(PlayerIdConst.ECSB_COOP_PLAYER_ID, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.WaitForCoopEnd -> unicast(
                PlayerIdConst.ECSB_COOP_PLAYER_ID,
                senderId,
                Message(PlayerIdConst.ECSB_COOP_PLAYER_ID, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.TravelAccept -> unicast(
                PlayerIdConst.ECSB_COOP_PLAYER_ID,
                senderId,
                Message(PlayerIdConst.ECSB_COOP_PLAYER_ID, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.TravelDeny -> unicast(
                PlayerIdConst.ECSB_COOP_PLAYER_ID,
                senderId,
                Message(PlayerIdConst.ECSB_COOP_PLAYER_ID, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.CoopFinish -> unicast(
                PlayerIdConst.ECSB_COOP_PLAYER_ID,
                senderId,
                Message(PlayerIdConst.ECSB_COOP_PLAYER_ID, message, sentAt)
            )

            is CoopMessages.CoopSystemOutputMessage.CancelPlanningAtAnyStage -> unicast(
                senderId,
                message.receiverId,
                Message(senderId, message, sentAt)
            )

            is ChatMessageADT.SystemOutputMessage.PlayerResourceChanged -> unicast(
                senderId,
                senderId,
                Message(senderId, message, sentAt)
            )

            TimeMessages.TimeSystemOutputMessage.GameTimeEnd -> broadcast(senderId, Message(senderId, message, sentAt))

            is TimeMessages.TimeSystemOutputMessage.GameTimeRemaining -> broadcast(
                senderId,
                Message(senderId, message, sentAt)
            )

            is TimeMessages.TimeSystemOutputMessage.GameTimeSyncResponse -> unicast(
                senderId,
                senderId,
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
                senderId,
                Message(senderId, message, sentAt)
            )
        }
    }

    private suspend fun sendToNearby(gameSessionId: GameSessionId, playerId: PlayerId, message: Message) {
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
