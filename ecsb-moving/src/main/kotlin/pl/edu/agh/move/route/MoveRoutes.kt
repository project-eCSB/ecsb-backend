package pl.edu.agh.move.route

import arrow.core.Either
import arrow.core.none
import arrow.core.raise.either
import arrow.core.some
import arrow.core.toOption
import arrow.fx.coroutines.parZip
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.auth.service.authWebSocketUserWS
import pl.edu.agh.domain.PlayerIdConst.ECSB_MOVING_PLAYER_ID
import pl.edu.agh.game.dao.GameSessionDao
import pl.edu.agh.game.dao.GameUserDao
import pl.edu.agh.game.service.GameService
import pl.edu.agh.game.service.GameStartCheck
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.move.MovementDataConnector
import pl.edu.agh.move.domain.MessageADT
import pl.edu.agh.move.domain.MoveMessage
import pl.edu.agh.move.domain.PlayerPositionWithClass
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.getLogger
import pl.edu.agh.websocket.service.WebSocketMainLoop.startMainLoop
import java.time.LocalDateTime

object MoveRoutes {
    fun Application.configureMoveRoutes(gameTokenConfig: JWTConfig<Token.GAME_TOKEN>) {
        val logger = getLogger(Application::class.java)
        val moveMessagePasser by inject<InteractionProducer<MoveMessage>>()
        val sessionStorage by inject<SessionStorage<WebSocketSession>>()
        val movementDataConnector by inject<MovementDataConnector>()
        val gameService by inject<GameService>()

        suspend fun initMovePlayer(
            webSocketUserParams: WebSocketUserParams,
            webSocketSession: WebSocketSession
        ): Either<String, Unit> {
            val (loginUserId, playerId, gameSessionId) = webSocketUserParams
            logger.info("Adding $playerId in game $gameSessionId to session storage")
            return GameStartCheck.checkGameStartedAndNotEnded(
                gameSessionId,
                playerId
            ) {
                sessionStorage.addSession(gameSessionId, playerId, webSocketSession)
                val playerStatus = gameService.getGameUserStatus(gameSessionId, loginUserId).getOrNull()!!
                val addMessage = MessageADT.SystemInputMessage.PlayerAdded.fromPlayerStatus(playerStatus)
                movementDataConnector.changeMovementData(gameSessionId, addMessage)
                moveMessagePasser.sendMessage(
                    gameSessionId,
                    playerId,
                    MoveMessage(
                        playerId,
                        addMessage,
                        LocalDateTime.now()
                    )
                )
            }(logger)
        }

        suspend fun closeConnection(webSocketUserParams: WebSocketUserParams) {
            val (userId, playerId, gameSessionId) = webSocketUserParams
            sessionStorage.removeSession(gameSessionId, playerId)
            gameService.removePlayerFromGameSession(
                gameSessionId,
                userId,
                false
            )
            moveMessagePasser.sendMessage(
                gameSessionId,
                playerId,
                MoveMessage(
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
                    moveMessagePasser.sendMessage(
                        gameSessionId,
                        playerId,
                        MoveMessage(
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

                    moveMessagePasser.sendMessage(
                        gameSessionId,
                        playerId,
                        MoveMessage(
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
