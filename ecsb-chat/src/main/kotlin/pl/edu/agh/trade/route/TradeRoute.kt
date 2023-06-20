package pl.edu.agh.trade.route

import arrow.core.getOrElse
import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.Message
import pl.edu.agh.chat.route.MessageValidationError
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerIdConst
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.trade.service.TradeService
import pl.edu.agh.utils.LoggerDelegate

class TradeRoute(private val messagePasser: MessagePasser<Message>, private val tradeService: TradeService) {
    private val logger by LoggerDelegate()

    private suspend fun cancelTrade(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        receiverId: PlayerId
    ) {
        tradeService.tradeCancel(gameSessionId, senderId, receiverId)
            .mapLeft {
                when (it) {
                    MessageValidationError.CheckFailed -> logger.info("Player interrupted happened for trade cancel sent to $receiverId in game $gameSessionId")
                    MessageValidationError.SamePlayer -> logger.info("Player $senderId sent trade cancel message to himself")
                }
            }.map { _ ->
                messagePasser.unicast(
                    gameSessionId = gameSessionId,
                    fromId = PlayerIdConst.ECSB_CHAT_PLAYER_ID,
                    toId = receiverId,
                    message = Message(
                        senderId,
                        ChatMessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeCancelMessage(receiverId)
                    )
                )
            }
    }

    private fun busyMessage(from: PlayerId, to: PlayerId): Message =
        Message(
            from,
            ChatMessageADT.OutputMessage.UserBusyMessage("Player $from is busy right now", to)
        )

    suspend fun handleTradeMessage(
        webSocketUserParams: WebSocketUserParams,
        message: ChatMessageADT.UserInputMessage.TradeMessage
    ) {
        val (_, senderId, gameSessionId) = webSocketUserParams
        when (message) {
            is ChatMessageADT.UserInputMessage.TradeMessage.TradeStartMessage -> {
                tradeService.tradeRequest(gameSessionId, senderId, message.receiverId).let {
                    it.mapLeft { validationError ->
                        when (validationError) {
                            MessageValidationError.CheckFailed -> messagePasser.unicast(
                                gameSessionId = gameSessionId,
                                fromId = PlayerIdConst.ECSB_CHAT_PLAYER_ID,
                                toId = senderId,
                                message = busyMessage(message.receiverId, senderId)
                            )

                            MessageValidationError.SamePlayer -> logger.info("Player $senderId sent $message message to himself")
                        }
                    }.map {
                        messagePasser.unicast(
                            gameSessionId = gameSessionId,
                            fromId = senderId,
                            toId = message.receiverId,
                            message = Message(senderId, message)
                        )
                    }
                }
            }

            is ChatMessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeStartAckMessage -> {
                tradeService.tradeAck(
                    gameSessionId,
                    senderId,
                    message.receiverId
                ).mapLeft {
                    when (it) {
                        MessageValidationError.CheckFailed -> messagePasser.unicast(
                            gameSessionId = gameSessionId,
                            fromId = PlayerIdConst.ECSB_CHAT_PLAYER_ID,
                            toId = senderId,
                            message = busyMessage(message.receiverId, senderId)
                        )

                        MessageValidationError.SamePlayer -> logger.info("Player $senderId sent $message message to himself")
                    }
                }.map { maybePlayerEquipments ->
                    val receiverId = message.receiverId
                    maybePlayerEquipments.map { playerEquipments ->
                        val messageForSender = Message(
                            receiverId,
                            ChatMessageADT.OutputMessage.TradeAckMessage(
                                false,
                                playerEquipments.receiverEquipment,
                                senderId
                            )
                        )
                        val messageForReceiver = Message(
                            senderId,
                            ChatMessageADT.OutputMessage.TradeAckMessage(
                                true,
                                playerEquipments.senderEquipment,
                                receiverId
                            )
                        )
                        messagePasser.unicast(
                            gameSessionId = gameSessionId,
                            fromId = senderId,
                            toId = receiverId,
                            message = messageForReceiver
                        )
                        messagePasser.unicast(
                            gameSessionId = gameSessionId,
                            fromId = receiverId,
                            toId = senderId,
                            message = messageForSender
                        )
                    }.getOrElse {
                        logger.warn("Equipments not found for $receiverId or $senderId")
                    }
                }
            }

            is ChatMessageADT.UserInputMessage.TradeMessage.TradeBidMessage -> {
                val receiverId = message.receiverId
                tradeService.tradeBid(gameSessionId, senderId, receiverId).mapLeft {
                    when (it) {
                        MessageValidationError.CheckFailed -> logger.info("Player interrupted happened for $message sent to $receiverId in game $gameSessionId")

                        MessageValidationError.SamePlayer -> logger.info("Player $senderId sent $message message to himself")
                    }
                }.map {
                    messagePasser.unicast(
                        gameSessionId = gameSessionId,
                        fromId = senderId,
                        toId = receiverId,
                        message = Message(senderId, message)
                    )
                }
            }

            is ChatMessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeCancelMessage -> cancelTrade(
                gameSessionId,
                senderId,
                message.receiverId
            )

            is ChatMessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeFinishMessage -> {
                val receiverId = message.receiverId
                tradeService.tradeFinalize(gameSessionId, senderId, receiverId, message.finalBid).mapLeft {
                    when (it) {
                        MessageValidationError.CheckFailed -> logger.info("Player interrupted happened for $message sent to $receiverId in game $gameSessionId")

                        MessageValidationError.SamePlayer -> logger.info("Player $senderId sent $message message to himself")
                    }
                }.map {
                    val messageForSender = Message(
                        senderId = PlayerIdConst.ECSB_CHAT_PLAYER_ID,
                        message = ChatMessageADT.OutputMessage.TradeFinishMessage(senderId)
                    )
                    val messageForReceiver = Message(
                        senderId = PlayerIdConst.ECSB_CHAT_PLAYER_ID,
                        message = ChatMessageADT.OutputMessage.TradeFinishMessage(receiverId)
                    )
                    messagePasser.unicast(
                        gameSessionId = gameSessionId,
                        fromId = PlayerIdConst.ECSB_CHAT_PLAYER_ID,
                        toId = receiverId,
                        message = messageForReceiver
                    )
                    messagePasser.unicast(
                        gameSessionId = gameSessionId,
                        fromId = PlayerIdConst.ECSB_CHAT_PLAYER_ID,
                        toId = senderId,
                        message = messageForSender
                    )
                }
            }
        }
    }

    suspend fun cancelAllPlayerTrades(gameSessionId: GameSessionId, playerId: PlayerId) {
        tradeService.cancelPlayerTrades(gameSessionId, playerId).onSome {
            cancelTrade(gameSessionId, playerId, it)
        }
    }
}
