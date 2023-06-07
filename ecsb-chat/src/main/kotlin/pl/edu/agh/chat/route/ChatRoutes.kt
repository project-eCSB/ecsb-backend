package pl.edu.agh.chat.route

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.option
import arrow.core.toNonEmptySetOrNone
import arrow.core.toOption
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
import pl.edu.agh.chat.domain.ProductionDto
import pl.edu.agh.chat.redis.InteractionDataConnector
import pl.edu.agh.chat.service.ProductionService
import pl.edu.agh.chat.service.TradeService
import pl.edu.agh.domain.*
import pl.edu.agh.domain.PlayerIdConst.ECSB_CHAT_PLAYER_ID
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.utils.Utils
import pl.edu.agh.utils.Utils.responsePair
import pl.edu.agh.utils.getLogger
import pl.edu.agh.websocket.service.WebSocketMainLoop.startMainLoop

object ChatRoutes {
    fun Application.configureChatRoutes(gameJWTConfig: JWTConfig<Token.GAME_TOKEN>) {
        val logger = getLogger(Application::class.java)
        val messagePasser by inject<MessagePasser<Message>>()
        val sessionStorage by inject<SessionStorage<WebSocketSession>>()
        val tradeService by inject<TradeService>()
        val productionService by inject<ProductionService>()
        val redisHashMapConnector: RedisHashMapConnector<GameSessionId, PlayerId, PlayerPosition> by inject()
        val interactionDataConnector: InteractionDataConnector by inject()

        fun initMovePlayer(webSocketUserParams: WebSocketUserParams, webSocketSession: WebSocketSession) {
            val (_, playerId, gameSessionId) = webSocketUserParams
            logger.info("Adding $playerId in game $gameSessionId to session storage")
            sessionStorage.addSession(gameSessionId, playerId, webSocketSession)
        }

        suspend fun checkIfPlayerBusy(gameSessionId: GameSessionId, playerId: PlayerId): Boolean {
            val receiverStatus = interactionDataConnector.findOne(gameSessionId, playerId)
            return receiverStatus.isSome { it.status != InteractionStatus.COMPANY_OFFER && it.status != InteractionStatus.TRADE_OFFER }
        }

        suspend fun checkIfPlayerInTrade(gameSessionId: GameSessionId, playerId: PlayerId): Boolean {
            val receiverStatus = interactionDataConnector.findOne(gameSessionId, playerId)
            return receiverStatus.isSome { it.status == InteractionStatus.TRADE_IN_PROGRESS }
        }

        suspend fun sendCancelMessage(
            gameSessionId: GameSessionId,
            senderId: PlayerId,
            message: MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeCancelMessage
        ) {
            val receiverId = message.receiverId
            if (senderId == receiverId) {
                logger.info("Player $senderId sent $message message to himself")
                return
            }
            if (checkIfPlayerInTrade(gameSessionId, receiverId)) {
                messagePasser.unicast(
                    gameSessionId = gameSessionId,
                    fromId = ECSB_CHAT_PLAYER_ID,
                    toId = receiverId,
                    message = Message(senderId, message)
                )
                interactionDataConnector.changeStatusData(
                    sessionId = gameSessionId,
                    senderId = senderId,
                    interaction = message
                )
            } else {
                log.info("Player interrupted happened for $message sent to $receiverId in game $gameSessionId")
            }
        }

        fun busyMessage(from: PlayerId, to: PlayerId): Message {
            return Message(
                from,
                MessageADT.OutputMessage.UserBusyMessage("Player $from is busy right now", to)
            )
        }

        suspend fun mainBlock(
            webSocketUserParams: WebSocketUserParams,
            message: MessageADT.UserInputMessage
        ) {
            val (_, senderId, gameSessionId) = webSocketUserParams
            logger.info("Received message: $message from ${webSocketUserParams.playerId} in ${webSocketUserParams.gameSessionId}")
            when (message) {
                is MessageADT.UserInputMessage.MulticastMessage -> {
                    either {
                        val playerPositions = redisHashMapConnector.getAll(gameSessionId)

                        val currentUserPosition =
                            playerPositions[senderId].toOption().toEither { "Current position not found" }.bind()

                        playerPositions.filter { (_, position) ->
                            position.coords.isInRange(currentUserPosition.coords, playersRange)
                        }.map { (playerId, _) -> playerId }.filterNot { it == senderId }.toNonEmptySetOrNone()
                            .toEither { "No players found to send message" }.bind()
                    }.fold(ifLeft = { err ->
                        logger.warn("Couldn't send message because $err")
                    }, ifRight = { nearbyPlayers ->
                            messagePasser.multicast(
                                gameSessionId = gameSessionId,
                                fromId = senderId,
                                toIds = nearbyPlayers,
                                message = Message(senderId, message)
                            )
                        })
                }

                is MessageADT.UserInputMessage.TradeMessage.TradeStartMessage -> {
                    val receiverId = message.receiverId
                    if (senderId == receiverId) {
                        logger.info("Player $senderId sent $message message to himself")
                        return
                    }
                    if (checkIfPlayerBusy(gameSessionId, receiverId)) {
                        messagePasser.unicast(
                            gameSessionId = gameSessionId,
                            fromId = ECSB_CHAT_PLAYER_ID,
                            toId = senderId,
                            message = busyMessage(receiverId, senderId)
                        )
                    } else {
                        messagePasser.unicast(
                            gameSessionId = gameSessionId,
                            fromId = senderId,
                            toId = receiverId,
                            message = Message(senderId, message)
                        )
                    }
                }

                is MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeStartAckMessage -> {
                    val receiverId = message.receiverId
                    if (senderId == receiverId) {
                        logger.info("Player $senderId sent $message message to himself")
                        return
                    }
                    if (checkIfPlayerBusy(gameSessionId, receiverId)) {
                        messagePasser.unicast(
                            gameSessionId = gameSessionId,
                            fromId = ECSB_CHAT_PLAYER_ID,
                            toId = senderId,
                            message = busyMessage(receiverId, senderId)
                        )
                    } else {
                        option {
                            val (senderEquipment, receiverEquipment) =
                                tradeService.getPlayersEquipmentsForTrade(gameSessionId, senderId, receiverId).bind()
                            val messageForSender = Message(
                                receiverId,
                                MessageADT.OutputMessage.TradeAckMessage(false, receiverEquipment, senderId)
                            )
                            val messageForReceiver = Message(
                                senderId,
                                MessageADT.OutputMessage.TradeAckMessage(true, senderEquipment, receiverId)
                            )
                            interactionDataConnector.changeStatusData(
                                sessionId = gameSessionId,
                                senderId = senderId,
                                interaction = message
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
                    if (senderId == receiverId) {
                        logger.info("Player $senderId sent $message message to himself")
                        return
                    }
                    if (checkIfPlayerInTrade(gameSessionId, receiverId)) {
                        messagePasser.unicast(
                            gameSessionId = gameSessionId,
                            fromId = senderId,
                            toId = receiverId,
                            message = Message(senderId, message)
                        )
                    } else {
                        log.info("Player interrupted happened for $message sent to $receiverId in game $gameSessionId")
                    }
                }

                is MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeCancelMessage -> sendCancelMessage(
                    gameSessionId,
                    senderId,
                    message
                )

                is MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeFinishMessage -> {
                    val receiverId = message.receiverId
                    if (senderId == receiverId) {
                        logger.info("Player $senderId sent $message message to himself")
                        return
                    }
                    if (checkIfPlayerInTrade(gameSessionId, receiverId)) {
                        val equipmentChanges = PlayerEquipment.getEquipmentChanges(
                            equipment1 = message.finalBid.senderRequest,
                            equipment2 = message.finalBid.senderOffer
                        )
                        tradeService.updatePlayerEquipment(
                            gameSessionId = gameSessionId,
                            playerId = senderId,
                            equipmentChanges = equipmentChanges
                        )
                        tradeService.updatePlayerEquipment(
                            gameSessionId = gameSessionId,
                            playerId = receiverId,
                            equipmentChanges = PlayerEquipment.getInverse(equipmentChanges)
                        )
                        val messageForSender = Message(
                            senderId = ECSB_CHAT_PLAYER_ID,
                            message = MessageADT.OutputMessage.TradeFinishMessage(senderId)
                        )
                        val messageForReceiver = Message(
                            senderId = ECSB_CHAT_PLAYER_ID,
                            message = MessageADT.OutputMessage.TradeFinishMessage(receiverId)
                        )
                        interactionDataConnector.changeStatusData(
                            sessionId = gameSessionId,
                            senderId = senderId,
                            interaction = message
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
                    } else {
                        log.info("Player interrupted happened for $message sent to $receiverId in game $gameSessionId")
                    }
                }
            }
        }

        suspend fun closeConnection(webSocketUserParams: WebSocketUserParams) {
            val (_, playerId, gameSessionId) = webSocketUserParams
            interactionDataConnector.findOne(gameSessionId, playerId).onSome {
                if (it.status == InteractionStatus.TRADE_IN_PROGRESS || it.status == InteractionStatus.COMPANY_IN_PROGRESS) {
                    sendCancelMessage(
                        gameSessionId,
                        playerId,
                        MessageADT.UserInputMessage.TradeMessage.ChangeStateMessage.TradeCancelMessage(it.otherPlayer)
                    )
                }
            }
            logger.info("Removing $playerId from $gameSessionId")
            sessionStorage.removeSession(gameSessionId, playerId)
            interactionDataConnector.removeInteractionData(gameSessionId, playerId)
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
                            val (gameSessionId, loginUserId) = getGameUser(call).toEither { HttpStatusCode.Unauthorized to "Couldn't find payload" }
                                .bind()
                            val (resourceName, quantity) = Utils.getBody<ProductionDto>(call).bind()

                            logger.info("User $loginUserId wants to conduct a production in game $gameSessionId")
                            productionService.conductPlayerProduction(
                                gameSessionId,
                                loginUserId,
                                resourceName,
                                quantity
                            ).mapLeft { it.toResponsePairLogging() }.bind()
                        }.responsePair()
                    }
                }
            }
        }
    }

    private const val playersRange = 3
}
