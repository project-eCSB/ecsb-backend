package pl.edu.agh.chat.route

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.auth.service.authWebSocketUserWS
import pl.edu.agh.auth.service.authenticate
import pl.edu.agh.auth.service.getGameUser
import pl.edu.agh.chat.domain.Message
import pl.edu.agh.chat.domain.MessageADT
import pl.edu.agh.chat.redis.InteractionDataConnector
import pl.edu.agh.chat.service.ProductionService
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.chat.service.TravelService
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerIdConst.ECSB_CHAT_PLAYER_ID
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.trade.service.TradeService
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.Utils
import pl.edu.agh.utils.Utils.responsePair
import pl.edu.agh.utils.getLogger
import pl.edu.agh.websocket.service.WebSocketMainLoop.startMainLoop

sealed class MessageValidationError() {
    object SamePlayer : MessageValidationError()
    object CheckFailed : MessageValidationError()
}

object ChatRoutes {
    fun Application.configureChatRoutes(gameJWTConfig: JWTConfig<Token.GAME_TOKEN>) {
        val logger = getLogger(Application::class.java)
        val messagePasser by inject<MessagePasser<Message>>()
        val sessionStorage by inject<SessionStorage<WebSocketSession>>()
        val tradeService by inject<TradeService>()
        val productionService by inject<ProductionService>()
        val travelService by inject<TravelService>()

        fun initMovePlayer(webSocketUserParams: WebSocketUserParams, webSocketSession: WebSocketSession) {
            val (_, playerId, gameSessionId) = webSocketUserParams
            logger.info("Adding $playerId in game $gameSessionId to session storage")
            sessionStorage.addSession(gameSessionId, playerId, webSocketSession)
        }

        suspend fun cancelTrade(
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
                        fromId = ECSB_CHAT_PLAYER_ID,
                        toId = receiverId,
                        message = Message(
                            senderId,
                            MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeCancelMessage(receiverId)
                        )
                    )
                }
        }

        fun busyMessage(from: PlayerId, to: PlayerId): Message =
            Message(
                from,
                MessageADT.OutputMessage.UserBusyMessage("Player $from is busy right now", to)
            )

        suspend fun handleTradeMessage(
            webSocketUserParams: WebSocketUserParams,
            message: MessageADT.UserInputMessage.TradeMessage
        ) {
            val (_, senderId, gameSessionId) = webSocketUserParams
            when (message) {
                is MessageADT.UserInputMessage.TradeMessage.TradeStartMessage -> {
                    tradeService.tradeRequest(gameSessionId, senderId, message.receiverId).let {
                        it.mapLeft { validationError ->
                            when (validationError) {
                                MessageValidationError.CheckFailed -> messagePasser.unicast(
                                    gameSessionId = gameSessionId,
                                    fromId = ECSB_CHAT_PLAYER_ID,
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

                is MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeStartAckMessage -> {
                    tradeService.tradeAck(
                        gameSessionId,
                        senderId,
                        message.receiverId
                    ).mapLeft {
                        when (it) {
                            MessageValidationError.CheckFailed -> messagePasser.unicast(
                                gameSessionId = gameSessionId,
                                fromId = ECSB_CHAT_PLAYER_ID,
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
                                MessageADT.OutputMessage.TradeAckMessage(
                                    false,
                                    playerEquipments.receiverEquipment,
                                    senderId
                                )
                            )
                            val messageForReceiver = Message(
                                senderId,
                                MessageADT.OutputMessage.TradeAckMessage(
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

                is MessageADT.UserInputMessage.TradeMessage.TradeBidMessage -> {
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

                is MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeCancelMessage -> cancelTrade(
                    gameSessionId,
                    senderId,
                    message.receiverId
                )

                is MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeFinishMessage -> {
                    val receiverId = message.receiverId
                    tradeService.tradeFinalize(gameSessionId, senderId, receiverId, message.finalBid).mapLeft {
                        when (it) {
                            MessageValidationError.CheckFailed -> logger.info("Player interrupted happened for $message sent to $receiverId in game $gameSessionId")

                            MessageValidationError.SamePlayer -> logger.info("Player $senderId sent $message message to himself")
                        }
                    }.map {
                        val messageForSender = Message(
                            senderId = ECSB_CHAT_PLAYER_ID,
                            message = MessageADT.OutputMessage.TradeFinishMessage(senderId)
                        )
                        val messageForReceiver = Message(
                            senderId = ECSB_CHAT_PLAYER_ID,
                            message = MessageADT.OutputMessage.TradeFinishMessage(receiverId)
                        )
                        messagePasser.unicast(
                            gameSessionId = gameSessionId,
                            fromId = ECSB_CHAT_PLAYER_ID,
                            toId = receiverId,
                            message = messageForReceiver
                        )
                        messagePasser.unicast(
                            gameSessionId = gameSessionId,
                            fromId = ECSB_CHAT_PLAYER_ID,
                            toId = senderId,
                            message = messageForSender
                        )
                    }
                }
            }
        }

        suspend fun mainBlock(
            webSocketUserParams: WebSocketUserParams,
            message: MessageADT.UserInputMessage
        ) {
            logger.info("Received message: $message from ${webSocketUserParams.playerId} in ${webSocketUserParams.gameSessionId}")
            when (message) {
                is MessageADT.UserInputMessage.TradeMessage -> handleTradeMessage(webSocketUserParams, message)
            }
        }

        suspend fun closeConnection(webSocketUserParams: WebSocketUserParams) {
            val (_, playerId, gameSessionId) = webSocketUserParams
            logger.info("Removing $playerId from $gameSessionId")
            tradeService.cancelPlayerTrades(gameSessionId, playerId).onSome {
                cancelTrade(gameSessionId, playerId, it)
            }
            sessionStorage.removeSession(gameSessionId, playerId)
        }

        routing {
            webSocket("/ws") {
                either<String, Unit> {
                    val webSocketUserParams = call.authWebSocketUserWS(gameJWTConfig).bind()

                    Either.catch {
                        startMainLoop(
                            logger,
                            MessageADT.UserInputMessage.serializer(),
                            webSocketUserParams,
                            ::initMovePlayer,
                            ::closeConnection,
                            ::mainBlock
                        )
                    }.mapLeft {
                        logger.error("Error while starting main loop: $it", it)
                        "Error initializing user"
                    }.bind()
                }.mapLeft {
                    return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, it))
                }
            }
            authenticate(Token.GAME_TOKEN, Role.USER) {
                post("/production") {
                    Utils.handleOutput(call) {
                        either {
                            val (gameSessionId, loginUserId, playerId) = getGameUser(call).toEither { HttpStatusCode.Unauthorized to "Couldn't find payload" }
                                .bind()
                            val quantity = Utils.getBody<Int>(call).bind()

                            logger.info("User $loginUserId wants to conduct a production in game $gameSessionId")
                            productionService.conductPlayerProduction(
                                gameSessionId,
                                loginUserId,
                                quantity,
                                playerId
                            ).mapLeft { it.toResponsePairLogging() }.bind()
                        }.responsePair()
                    }
                }
                post("/travel") {
                    Utils.handleOutput(call) {
                        either {
                            val (gameSessionId, loginUserId) = getGameUser(call).toEither { HttpStatusCode.Unauthorized to "Couldn't find payload" }
                                .bind()
                            val gameCityName = Utils.getBody<TravelName>(call).bind()

                            logger.info("User $loginUserId conducts travel to $gameCityName in game $gameSessionId")
                            travelService.conductPlayerTravel(
                                gameSessionId,
                                loginUserId,
                                gameCityName
                            ).mapLeft { it.toResponsePairLogging() }.bind()
                        }.responsePair()
                    }
                }
            }
        }
    }
}
