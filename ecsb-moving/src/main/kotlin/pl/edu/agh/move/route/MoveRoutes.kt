package pl.edu.agh.move.route

import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.auth.service.authWebSocketUser
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.PlayerIdConst.ECSB_MOVING_PLAYER_ID
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.move.domain.Message
import pl.edu.agh.move.domain.MessageADT
import pl.edu.agh.redis.RedisConnector
import pl.edu.agh.utils.getLogger
import pl.edu.agh.websocket.service.WebSocketMainLoop.startMainLoop
import java.time.LocalDateTime

object MoveRoutes {
    fun Application.configureMoveRoutes() {
        val logger = getLogger(Application::class.java)
        val messagePasser by inject<MessagePasser<Message>>()
        val sessionStorage by inject<SessionStorage<WebSocketSession>>()
        val redisConnector by inject<RedisConnector>()

        suspend fun initMovePlayer(webSocketUserParams: WebSocketUserParams, webSocketSession: WebSocketSession): Unit {
            val (playerId, gameSessionId) = webSocketUserParams
            logger.info("Adding $playerId in game $gameSessionId to session storage")
            sessionStorage.addSession(gameSessionId, playerId, webSocketSession)
            val addMessage = MessageADT.SystemInputMessage.PlayerAdded(playerId, Coordinates(3, 3))
            redisConnector.changeMovementData(
                gameSessionId,
                addMessage
            )
            messagePasser.broadcast(
                gameSessionId,
                playerId,
                Message(
                    playerId,
                    addMessage,
                    LocalDateTime.now()
                )
            )
        }

        suspend fun closeConnection(webSocketUserParams: WebSocketUserParams) {
            val (playerId, gameSessionId) = webSocketUserParams
            logger.info("Removing $playerId from $gameSessionId")
            sessionStorage.removeSession(gameSessionId, playerId)
            redisConnector.changeMovementData(
                gameSessionId,
                MessageADT.SystemInputMessage.PlayerRemove(playerId)
            )
            messagePasser.broadcast(
                gameSessionId,
                playerId,
                Message(
                    playerId,
                    MessageADT.SystemInputMessage.PlayerRemove(playerId),
                    LocalDateTime.now()
                )
            )
        }

        suspend fun mainBlock(webSocketUserParams: WebSocketUserParams, message: MessageADT.UserInputMessage) {
            val (playerId, gameSessionId) = webSocketUserParams
            logger.info("Received message: $message from ${webSocketUserParams.playerId} in ${webSocketUserParams.gameSessionId}")
            when (message) {
                is MessageADT.UserInputMessage.Move -> {
                    logger.info("Player $playerId moved in $gameSessionId: $message")
                    messagePasser.broadcast(
                        gameSessionId,
                        playerId,
                        Message(
                            playerId,
                            MessageADT.OutputMessage.PlayerMoved(playerId, message.coords),
                            LocalDateTime.now()
                        )
                    )
                    redisConnector.changeMovementData(gameSessionId, playerId, message)
                }

                is MessageADT.UserInputMessage.SyncRequest -> {
                    logger.info("Player $playerId requested sync in $gameSessionId")
                    val data = redisConnector.getAllMovementData(gameSessionId)
                    messagePasser.unicast(
                        gameSessionId,
                        ECSB_MOVING_PLAYER_ID,
                        playerId,
                        Message(
                            ECSB_MOVING_PLAYER_ID,
                            data,
                            LocalDateTime.now()
                        )
                    )
                }
            }
        }

        routing {
            webSocket("/ws") {
                either<String, Unit> {
                    val webSocketUserParams = call.authWebSocketUser().bind()

                    startMainLoop<MessageADT.UserInputMessage>(
                        logger,
                        MessageADT.UserInputMessage.serializer(),
                        webSocketUserParams,
                        ::initMovePlayer,
                        ::closeConnection,
                        ::mainBlock
                    )
                }.mapLeft {
                    return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, it))
                }
            }
        }
    }
}
