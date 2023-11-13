package pl.edu.agh.move.route

import arrow.core.Either
import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.auth.service.authWebSocketUserWS
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.move.MovementDataConnector
import pl.edu.agh.move.domain.MessageADT
import pl.edu.agh.moving.domain.PlayerStatus
import pl.edu.agh.utils.getLogger
import pl.edu.agh.websocket.service.WebSocketMainLoop.startMainLoop

object MoveRoutes {
    fun Application.configureMoveRoutes(
        gameTokenConfig: JWTConfig<Token.GAME_TOKEN>,
        moveMessagePasser: InteractionProducer<MessageADT>,
        sessionStorage: SessionStorage<WebSocketSession>,
        movementDataConnector: MovementDataConnector
    ) {
        val logger = getLogger(Application::class.java)

        suspend fun initMovePlayer(
            webSocketUserParams: WebSocketUserParams,
            webSocketSession: WebSocketSession
        ): Either<String, Unit> = either {
            val (_, playerId, gameSessionId, className, gameValid) = webSocketUserParams
            logger.info("Adding $playerId in game $gameSessionId to session storage")
            (if (gameValid.not()) {
                logger.error("Connected to move ws even though: Gra nie rozpoczeta albo zakonczona")
                Either.Left("Gra nie rozpoczeta albo zakonczona")
            } else {
                Either.Right(Unit)
            }).bind()
            sessionStorage.addSession(gameSessionId, playerId, webSocketSession)
            val playerStatus = movementDataConnector.getMovementData(gameSessionId, playerId).getOrNull()!!
                .let { PlayerStatus(it.coords, it.direction, className, playerId) }
            val addMessage = MessageADT.SystemInputMessage.PlayerAdded.fromPlayerStatus(playerStatus)
            movementDataConnector.changeMovementData(gameSessionId, className, addMessage)
            moveMessagePasser.sendMessage(
                gameSessionId,
                playerId,
                addMessage
            )
        }

        suspend fun closeConnection(webSocketUserParams: WebSocketUserParams) {
            val (_, playerId, gameSessionId) = webSocketUserParams
            sessionStorage.removeSession(gameSessionId, playerId)
            Either.catch { movementDataConnector.setAsInactive(gameSessionId, playerId) }
                .onLeft { logger.error("Set as inactive failed due to $it", it) }
            moveMessagePasser.sendMessage(
                gameSessionId,
                playerId,
                MessageADT.SystemInputMessage.PlayerRemove(playerId)
            )
        }

        suspend fun mainBlock(webSocketUserParams: WebSocketUserParams, message: MessageADT.UserInputMessage) {
            val (_, playerId, gameSessionId, className) = webSocketUserParams
            logger.info("Received message: $message from ${webSocketUserParams.playerId} in ${webSocketUserParams.gameSessionId}")
            when (message) {
                is MessageADT.UserInputMessage.Move -> {
                    logger.info("Player $playerId moved in $gameSessionId: $message")
                    moveMessagePasser.sendMessage(
                        gameSessionId,
                        playerId,
                        MessageADT.OutputMessage.PlayerMoved(playerId, message.coords, message.direction)
                    )
                    movementDataConnector.changeMovementData(gameSessionId, playerId, className, message)
                }

                is MessageADT.UserInputMessage.SyncRequest -> {
                    logger.info("Player $playerId requested sync in $gameSessionId")

                    val messageADT =
                        movementDataConnector.getAllMovementDataIfActive(gameSessionId)
                            .let {
                                MessageADT.OutputMessage.PlayersSync(it)
                            }

                    moveMessagePasser.sendMessage(
                        gameSessionId,
                        playerId,
                        messageADT
                    )
                }
            }
        }

        routing {
            webSocket("/ws") {
                either<String, Unit> {
                    val webSocketUserParams = call.authWebSocketUserWS(gameTokenConfig).bind()

                    Either.catch {
                        startMainLoop<MessageADT.UserInputMessage>(
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
        }
    }
}
