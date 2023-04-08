package pl.edu.agh.move.route

import arrow.core.Either
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.raise.option
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerIdConst.ECSB_MOVING_PLAYER_ID
import pl.edu.agh.messages.domain.MessageSenderData
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.move.domain.Message
import pl.edu.agh.move.domain.MessageADT
import pl.edu.agh.redis.RedisConnector
import pl.edu.agh.utils.Utils.getOption
import pl.edu.agh.utils.getLogger
import java.time.LocalDateTime

object MoveRoutes {
    fun Application.configureMoveRoutes() {
        val logger = getLogger(Application::class.java)
        val messagePasser by inject<MessagePasser<Message>>()
        val sessionStorage by inject<SessionStorage<MessageSenderData>>()
        val redisConnector by inject<RedisConnector>()

        routing {
            webSocket("/ws") {
                // get params needed for auth
                val (playerId, gameSessionId) = option {
                    val playerId = call.parameters.getOption("name").map { PlayerId(it) }.bind()
                    val gameSessionId =
                        call.parameters.getOption("gameSessionId").flatMap { Option.fromNullable(it.toIntOrNull()) }
                            .map { GameSessionId(it) }.bind()

                    (playerId to gameSessionId)
                }.getOrElse {
                    return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "parameters are required"))
                }

                // accept and proceed with messagePassing
                logger.info("Adding $playerId in game $gameSessionId to session storage")
                sessionStorage.addSession(gameSessionId, playerId, this)
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
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            Either.catch {
                                Json.decodeFromString<MessageADT.UserInputMessage>(text)
                            }.mapLeft {
                                logger.error("Error while decoding message: $it", it)
                            }.map {
                                when (it) {
                                    is MessageADT.UserInputMessage.Move -> {
                                        logger.info("Player $playerId moved in $gameSessionId: $it")
                                        messagePasser.broadcast(
                                            gameSessionId, playerId, Message(
                                                playerId,
                                                MessageADT.OutputMessage.PlayerMoved(playerId, it.coords),
                                                LocalDateTime.now()
                                            )
                                        )
                                        redisConnector.changeMovementData(gameSessionId, playerId, it)
                                    }

                                    is MessageADT.UserInputMessage.SyncRequest -> {
                                        logger.info("Player $playerId requested sync in $gameSessionId")
                                        val data = redisConnector.getAllMovementData(gameSessionId)
                                        val message = Message(
                                            ECSB_MOVING_PLAYER_ID,
                                            data,
                                            LocalDateTime.now()
                                        )
                                        this.outgoing.send(
                                            Frame.Text(
                                                Json.encodeToString(
                                                    Message.serializer(), message
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Main loop have thrown exception: $e", e)
                } finally {
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
            }
        }
    }
}