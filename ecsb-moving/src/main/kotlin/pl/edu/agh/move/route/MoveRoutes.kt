package pl.edu.agh.move.route

import arrow.core.Either
import arrow.core.continuations.option
import arrow.core.getOrElse
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import pl.edu.agh.messages.domain.MessageSenderData
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.move.domain.Message
import pl.edu.agh.move.domain.MessageADT
import pl.edu.agh.utils.Utils.getOption
import pl.edu.agh.utils.getLogger
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

object MoveRoutes {
    fun Application.configureMoveRoutes() {
        val logger = getLogger(Application::class.java)
        val messagePasser by inject<MessagePasser<Message>>()
        val sessionStorage by inject<SessionStorage<MessageSenderData>>()

        routing {
            webSocket("/ws") {
                //get params needed for auth
                val name = option {
                    call.parameters.getOption("name").bind()
                }.getOrElse {
                    return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "parameters are required"))
                }

                //accept and proceed with messagePassing
                val sessionData = sessionStorage.addSession(this)
                messagePasser.broadcast(
                    sessionData,
                    Message(sessionData, MessageADT.PlayerAdded(name, 3, 3), LocalDateTime.now())
                )
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val sentAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                            val text = frame.readText()
                            logger.info("text sent by: $sessionData - [$text]")

                            Either.catch {
                                Json.decodeFromString<MessageADT>(text)
                            }.map {
                                print("broadcasting: $it")
                                messagePasser.broadcast(sessionData, Message(sessionData, it, sentAt))
                            }.mapLeft {
                                logger.error("Error while decoding message: $it", it)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Main loop have thrown exception: $e", e)
                } finally {
                    logger.info("removing $sessionData")
                    messagePasser.broadcast(
                        sessionData,
                        Message(sessionData, MessageADT.PlayerRemove(name), LocalDateTime.now())
                    )
                    sessionStorage.removeSession(sessionData)
                }
            }
        }

    }
}

