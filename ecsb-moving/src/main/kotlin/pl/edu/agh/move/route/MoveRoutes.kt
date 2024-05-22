package pl.edu.agh.move.route

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.toOption
import arrow.fx.coroutines.parZip
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.auth.service.authWebSocketUserWS
import pl.edu.agh.game.dao.GameUserDao
import pl.edu.agh.game.service.GameStartCheck
import pl.edu.agh.game.service.GameUserService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.move.MovementDataConnector
import pl.edu.agh.move.domain.MoveMessageADT
import pl.edu.agh.move.domain.PlayerPositionWithClass
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.getLogger
import pl.edu.agh.websocket.service.WebSocketMainLoop.startMainLoop

object MoveRoutes {
    fun Application.configureMoveRoutes(
        gameTokenConfig: JWTConfig<Token.GAME_TOKEN>,
        moveMessagePasser: InteractionProducer<MoveMessageADT>
    ) {
        val logger = getLogger(Application::class.java)
        val moveSessionStorage by inject<SessionStorage<WebSocketSession>>()
        val movementDataConnector by inject<MovementDataConnector>()
        val gameUserService by inject<GameUserService>()

        suspend fun initMovePlayer(
            webSocketUserParams: WebSocketUserParams,
            webSocketSession: WebSocketSession
        ): Either<String, Unit> = either {
            val (loginUserId, playerId, gameSessionId) = webSocketUserParams
            logger.info("Adding $playerId in game $gameSessionId to moving session storage")
            GameStartCheck.checkGameStartedAndNotEnded(
                gameSessionId,
                playerId
            ) { moveSessionStorage.addSession(gameSessionId, playerId, webSocketSession) }(logger).bind()
            gameUserService.setInGame(gameSessionId, playerId).bind()
            val playerStatus = gameUserService.getGameUserStatus(gameSessionId, loginUserId).getOrNull()!!
            val addMessage = MoveMessageADT.SystemInputMoveMessage.PlayerAdded.fromPlayerStatus(playerStatus)
            movementDataConnector.changeMovementData(gameSessionId, addMessage)
            moveMessagePasser.sendMessage(
                gameSessionId,
                playerId,
                addMessage
            )
        }

        this.environment.monitor.subscribe(ApplicationStopPreparing) {
            logger.info("Closing all connections")
            runBlocking {
                moveSessionStorage.getAllSessions()
                    .forEach { (_, players) ->
                        players.forEach { (_, session) ->
                            session.close(CloseReason(CloseReason.Codes.SERVICE_RESTART, "Server restart"))
                        }
                    }
            }
        }

        suspend fun closeConnection(webSocketUserParams: WebSocketUserParams) {
            val (_, playerId, gameSessionId) = webSocketUserParams
            logger.info("Removing $playerId from game $gameSessionId moving session storage")
            moveSessionStorage.removeSession(gameSessionId, playerId)
            gameUserService.removePlayerFromGameSession(
                gameSessionId,
                playerId,
                false
            )
            moveMessagePasser.sendMessage(
                gameSessionId,
                playerId,
                MoveMessageADT.SystemInputMoveMessage.PlayerRemove(playerId)
            )
        }

        suspend fun mainBlock(webSocketUserParams: WebSocketUserParams, message: MoveMessageADT.UserInputMoveMessage) {
            val (_, playerId, gameSessionId) = webSocketUserParams
            logger.info("Received message: $message from ${webSocketUserParams.playerId} in ${webSocketUserParams.gameSessionId}")
            when (message) {
                is MoveMessageADT.UserInputMoveMessage.Move -> {
                    logger.info("Player $playerId moved in $gameSessionId: $message")
                    moveMessagePasser.sendMessage(
                        gameSessionId,
                        playerId,
                        MoveMessageADT.OutputMoveMessage.PlayerMoved(playerId, message.coords, message.direction),
                    )
                    movementDataConnector.changeMovementData(gameSessionId, playerId, message)
                }

                is MoveMessageADT.UserInputMoveMessage.SyncRequest -> {
                    logger.info("Player $playerId requested sync in $gameSessionId")

                    val moveMessageADT = parZip(
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
                            }.let { MoveMessageADT.OutputMoveMessage.PlayersSync(it) }
                    }

                    moveMessagePasser.sendMessage(
                        gameSessionId,
                        playerId,
                        moveMessageADT,
                    )
                }
            }
        }

        routing {
            webSocket("/ws") {
                either<String, Unit> {
                    val webSocketUserParams = call.authWebSocketUserWS(gameTokenConfig).bind()

                    Either.catch {
                        startMainLoop(
                            logger,
                            MoveMessageADT.UserInputMoveMessage.serializer(),
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
                    logger.error("Error on movement ws: $it")
                    return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, it))
                }
            }
        }
    }
}
