package pl.edu.agh.landingPage

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.auth.service.authWebSocketUserWS
import pl.edu.agh.domain.AmountDiff
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.GameSessionDao
import pl.edu.agh.game.domain.GameStatus
import pl.edu.agh.game.service.GameStartService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.landingPage.domain.LandingPageMessage
import pl.edu.agh.logs.domain.LogsMessage
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.redis.RedisJsonConnector
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.getLogger
import pl.edu.agh.websocket.service.WebSocketMainLoop.startMainLoop
import java.util.concurrent.atomic.AtomicLong

object LandingPageRoutes {
    fun Application.configureLandingPageRoutes(
        gameJWTConfig: JWTConfig<Token.GAME_TOKEN>,
        landingPageSessionStorage: SessionStorage<WebSocketSession>,
        interactionProducer: InteractionProducer<LandingPageMessage>,
        redisJsonConnector: RedisJsonConnector<PlayerId, PlayerId>,
        playerCountGauge: AtomicLong
    ) {
        val logger = getLogger(Application::class.java)
        val gameStartService by inject<GameStartService>()
        val logsProducer by inject<InteractionProducer<LogsMessage>>()

        suspend fun syncPlayers(gameSessionId: GameSessionId, playerId: PlayerId) = either {
            val maybeGameStatus =
                Transactor.dbQuery { GameSessionDao.getGameStatus(gameSessionId) }.getOrElse { GameStatus.ENDED }
            when (maybeGameStatus) {
                GameStatus.NOT_STARTED -> {
                    val people = redisJsonConnector.getAll(gameSessionId).values.toList()
                    val actualAmount = people.size.nonNeg
                    val minAmountToStart = Transactor.dbQuery { GameSessionDao.getUsersMinAmountToStart(gameSessionId) }
                        .toEither { "Error while getting min amount of players to start" }.bind()

                    interactionProducer.sendMessage(
                        gameSessionId,
                        playerId,
                        LandingPageMessage.LandingPageMessageMain(AmountDiff(actualAmount, minAmountToStart), people)
                    )
                    if (actualAmount.value >= minAmountToStart.value) {
                        logger.info("Starting game $gameSessionId")
                        gameStartService.startGame(gameSessionId).toEither { "Error starting game $gameSessionId" }.bind()
                    }
                }

                GameStatus.STARTED -> {
                    interactionProducer.sendMessage(gameSessionId, playerId, LandingPageMessage.GameStarted)
                }

                GameStatus.ENDED -> {
                    interactionProducer.sendMessage(gameSessionId, playerId, LandingPageMessage.GameEnded)
                }
            }
        }

        suspend fun initLobbyPlayer(
            webSocketUserParams: WebSocketUserParams,
            webSocketSession: WebSocketSession
        ): Either<String, Unit> = either {
            playerCountGauge.incrementAndGet()
            val (_, playerId, gameSessionId) = webSocketUserParams
            logger.info("Adding $playerId in game $gameSessionId to landing session storage")
            landingPageSessionStorage.addSession(gameSessionId, playerId, webSocketSession)
            redisJsonConnector.changeData(gameSessionId, playerId, playerId)
            logsProducer.sendMessage(gameSessionId, playerId, LogsMessage.UserJoinedLobby(playerId))
            syncPlayers(gameSessionId, playerId).bind()
        }

        fun mainBlock(
            webSocketUserParams: WebSocketUserParams,
            message: String
        ) {
            logger.info("Received message: $message from ${webSocketUserParams.playerId} in ${webSocketUserParams.gameSessionId}")
        }

        suspend fun closeConnection(webSocketUserParams: WebSocketUserParams) {
            val (_, playerId, gameSessionId) = webSocketUserParams
            logger.info("Removing $playerId from $gameSessionId landing page session storage")
            landingPageSessionStorage.removeSession(gameSessionId, playerId)
            redisJsonConnector.removeElement(gameSessionId, playerId)
            logsProducer.sendMessage(gameSessionId, playerId, LogsMessage.UserLeftLobby(playerId))
            syncPlayers(gameSessionId, playerId)
            playerCountGauge.decrementAndGet()
        }

        this.environment.monitor.subscribe(ApplicationStopPreparing) {
            logger.info("Closing all connections")
            runBlocking {
                landingPageSessionStorage.getAllSessions()
                    .forEach { (_, players) ->
                        players.forEach { (_, session) ->
                            session.close(CloseReason(CloseReason.Codes.SERVICE_RESTART, "Server restart"))
                        }
                    }
            }
        }

        routing {
            webSocket("/landing/ws") {
                either<String, Unit> {
                    val webSocketUserParams = call.authWebSocketUserWS(gameJWTConfig).bind()

                    Either.catch {
                        startMainLoop(
                            logger,
                            String.serializer(),
                            webSocketUserParams,
                            ::initLobbyPlayer,
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
