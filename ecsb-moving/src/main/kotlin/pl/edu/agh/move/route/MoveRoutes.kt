package pl.edu.agh.move.route

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.toOption
import arrow.fx.coroutines.parZip
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.auth.service.authWebSocketUserWS
import pl.edu.agh.auth.service.getJWTConfig
import pl.edu.agh.domain.PlayerIdConst.ECSB_MOVING_PLAYER_ID
import pl.edu.agh.game.dao.GameUserDao
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.move.domain.Message
import pl.edu.agh.move.domain.MessageADT
import pl.edu.agh.move.domain.PlayerPositionWithClass
import pl.edu.agh.redis.MovementDataConnector
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.getLogger
import pl.edu.agh.websocket.service.WebSocketMainLoop.startMainLoop
import java.time.LocalDateTime

object MoveRoutes {
    fun Application.configureMoveRoutes() {
        val logger = getLogger(Application::class.java)
        val messagePasser by inject<MessagePasser<Message>>()
        val sessionStorage by inject<SessionStorage<WebSocketSession>>()
        val movementDataConnector by inject<MovementDataConnector>()
        val gameJWTConfig = this.getJWTConfig(Token.GAME_TOKEN)

        suspend fun initMovePlayer(webSocketUserParams: WebSocketUserParams, webSocketSession: WebSocketSession) {
            val (loginUserId, playerId, gameSessionId) = webSocketUserParams
            logger.info("Adding $playerId in game $gameSessionId to session storage")
            sessionStorage.addSession(gameSessionId, playerId, webSocketSession)

            val playerStatus =
                Transactor.dbQuery { GameUserDao.getGameUserInfo(loginUserId, gameSessionId).getOrNull()!! }
            val addMessage = MessageADT.SystemInputMessage.PlayerAdded.fromPlayerStatus(playerStatus)

            movementDataConnector.changeMovementData(gameSessionId, addMessage)
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
            val (_, playerId, gameSessionId) = webSocketUserParams
            logger.info("Removing $playerId from $gameSessionId")
            sessionStorage.removeSession(gameSessionId, playerId)
            movementDataConnector.changeMovementData(
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
            val (_, playerId, gameSessionId) = webSocketUserParams
            logger.info("Received message: $message from ${webSocketUserParams.playerId} in ${webSocketUserParams.gameSessionId}")
            when (message) {
                is MessageADT.UserInputMessage.Move -> {
                    logger.info("Player $playerId moved in $gameSessionId: $message")
                    messagePasser.broadcast(
                        gameSessionId,
                        playerId,
                        Message(
                            playerId,
                            MessageADT.OutputMessage.PlayerMoved(playerId, message.coords, message.direction),
                            LocalDateTime.now()
                        )
                    )
                    movementDataConnector.changeMovementData(gameSessionId, playerId, message)
                }

                is MessageADT.UserInputMessage.SyncRequest -> {
                    logger.info("Player $playerId requested sync in $gameSessionId")

                    val messageADT = parZip(
                        {
                            movementDataConnector.getAllMovementData(gameSessionId)
                        },
                        {
                            Transactor.dbQuery {
                                GameUserDao
                                    .getAllUsersInGame(gameSessionId)
                                    .groupBy({ it.playerId }, { it.className })
                                    .flatMap { (playerId, values) -> values.map { playerId to it } }
                                    .toMap()
                            }
                        }
                    ) { movementData, playerData ->
                        movementData
                            .flatMap { playerPosition ->
                                playerData[playerPosition.id]
                                    .toOption()
                                    .map { PlayerPositionWithClass(it, playerPosition) }
                                    .toList()
                            }.let { MessageADT.OutputMessage.PlayersSync(it) }
                    }

                    messagePasser.unicast(
                        gameSessionId,
                        ECSB_MOVING_PLAYER_ID,
                        playerId,
                        Message(
                            ECSB_MOVING_PLAYER_ID,
                            messageADT,
                            LocalDateTime.now()
                        )
                    )
                }
            }
        }

        routing {
            webSocket("/ws") {
                either<String, Unit> {
                    val webSocketUserParams = call.authWebSocketUserWS(gameJWTConfig).bind()

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
