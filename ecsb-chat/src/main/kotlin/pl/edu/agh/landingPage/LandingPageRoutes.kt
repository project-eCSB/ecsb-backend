package pl.edu.agh.landingPage

import arrow.core.Either
import arrow.core.Some
import arrow.core.getOrElse
import arrow.core.raise.either
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.builtins.serializer
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.auth.service.authWebSocketUserWS
import pl.edu.agh.coop.domain.AmountDiff
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.LogsMessage
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.GameSessionDao
import pl.edu.agh.game.domain.GameStatus
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.landingPage.domain.LandingPageMessage
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.redis.RedisJsonConnector
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.getLogger
import pl.edu.agh.websocket.service.WebSocketMainLoop.startMainLoop


object LandingPageRoutes {
    fun Application.configureLandingPageRoutes(
        gameJWTConfig: JWTConfig<Token.GAME_TOKEN>,
        sessionStorage: SessionStorage<WebSocketSession>,
        interactionProducer: InteractionProducer<LandingPageMessage>,
        redisJsonConnector: RedisJsonConnector<PlayerId, PlayerId>
    ) {
        val logger = getLogger(Application::class.java)
        val logsProducer by inject<InteractionProducer<LogsMessage>>()

        suspend fun syncPlayers(gameSessionId: GameSessionId, playerId: PlayerId) = either {
            val maybeGameStatus = Transactor.dbQuery { GameSessionDao.getGameStatus(gameSessionId) }.getOrElse { GameStatus.ENDED }
            when (maybeGameStatus) {
                GameStatus.NOT_STARTED -> {
                    val people = redisJsonConnector.getAll(gameSessionId).values.toList()
                    val actualAmount = people.size.nonNeg
                    val maxAmount = Transactor.dbQuery { GameSessionDao.getUsersMaxAmount(gameSessionId) }
                        .toEither { "Error while getting max amount of users" }.bind()

                    interactionProducer.sendMessage(
                        gameSessionId,
                        playerId,
                        LandingPageMessage.LandingPageMessageMain(AmountDiff(actualAmount, maxAmount), people)
                    )
                }
                GameStatus.STARTED -> {
                    interactionProducer.sendMessage(gameSessionId, playerId, LandingPageMessage.GameStarted)
                }
                GameStatus.ENDED -> {
                    interactionProducer.sendMessage(gameSessionId, playerId, LandingPageMessage.GameEnded)
                }
            }

        }

        suspend fun initMovePlayer(
            webSocketUserParams: WebSocketUserParams,
            webSocketSession: WebSocketSession
        ): Either<String, Unit> = either {
            val (_, playerId, gameSessionId) = webSocketUserParams
            logger.info("Adding $playerId in game $gameSessionId to session storage")
            sessionStorage.addSession(gameSessionId, playerId, webSocketSession)
            redisJsonConnector.changeData(gameSessionId, playerId, playerId)
            logsProducer.sendMessage(gameSessionId, playerId, LogsMessage.UserJoinedLobby(playerId))
            syncPlayers(gameSessionId, playerId)
            Unit
        }

        fun mainBlock(
            webSocketUserParams: WebSocketUserParams,
            message: String
        ) {
            logger.info("Received message: $message from ${webSocketUserParams.playerId} in ${webSocketUserParams.gameSessionId}")
        }

        suspend fun closeConnection(webSocketUserParams: WebSocketUserParams) {
            val (_, playerId, gameSessionId) = webSocketUserParams
            logger.info("Removing $playerId from $gameSessionId")
            sessionStorage.removeSession(gameSessionId, playerId)
            redisJsonConnector.removeElement(gameSessionId, playerId)
            logsProducer.sendMessage(gameSessionId, playerId, LogsMessage.UserLeftLobby(playerId))
            syncPlayers(gameSessionId, playerId)
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
