package pl.edu.agh.chat.route

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.toNonEmptySetOrNone
import arrow.core.toOption
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.auth.service.authWebSocketUser
import pl.edu.agh.chat.domain.Message
import pl.edu.agh.chat.domain.MessageADT
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.utils.getLogger
import pl.edu.agh.websocket.service.WebSocketMainLoop.startMainLoop

object ChatRoutes {
    fun Application.configureChatRoutes() {
        val logger = getLogger(Application::class.java)
        val messagePasser by inject<MessagePasser<Message>>()
        val sessionStorage by inject<SessionStorage<WebSocketSession>>()
        val redisHashMapConnector: RedisHashMapConnector<GameSessionId, PlayerId, PlayerPosition> by inject()

        suspend fun initMovePlayer(webSocketUserParams: WebSocketUserParams, webSocketSession: WebSocketSession) {
            val (playerId, gameSessionId) = webSocketUserParams
            logger.info("Adding $playerId in game $gameSessionId to session storage")
            sessionStorage.addSession(gameSessionId, playerId, webSocketSession)
        }

        suspend fun closeConnection(webSocketUserParams: WebSocketUserParams) {
            val (playerId, gameSessionId) = webSocketUserParams
            logger.info("Removing $playerId from $gameSessionId")
            sessionStorage.removeSession(gameSessionId, playerId)
        }

        suspend fun mainBlock(webSocketUserParams: WebSocketUserParams, message: MessageADT) {
            val (playerId, gameSessionId) = webSocketUserParams
            logger.info("Received message: $message from ${webSocketUserParams.playerId} in ${webSocketUserParams.gameSessionId}")
            when (message) {
                is MessageADT.MulticastMessage -> {
                    either {
                        val playerPositions = redisHashMapConnector.getAll(gameSessionId)

                        val currentUserPosition =
                            playerPositions[playerId].toOption().toEither { "Current position not found" }.bind()

                        playerPositions.filter { (_, position) ->
                            position.coords.isInRange(currentUserPosition.coords, playersRange)
                        }.map { (playerId, _) -> playerId }.filterNot { it == playerId }.toNonEmptySetOrNone()
                            .toEither { "No players found to send message" }.bind()
                    }.fold(ifLeft = { err ->
                        logger.warn("Couldnt send message because $err")
                    }, ifRight = { nearbyPlayers ->
                            messagePasser.multicast(
                                gameSessionId,
                                playerId,
                                nearbyPlayers,
                                Message(playerId, message)
                            )
                        })
                }

                is MessageADT.UnicastMessage -> messagePasser.unicast(
                    gameSessionId,
                    playerId,
                    message.sendTo,
                    Message(playerId, message)
                )
            }
        }

        routing {
            webSocket("/ws") {
                either<String, Unit> {
                    val webSocketUserParams = call.authWebSocketUser().bind()

                    Either.catch {
                        startMainLoop<MessageADT>(
                            logger,
                            MessageADT.serializer(),
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

    private val playersRange = 3
}
