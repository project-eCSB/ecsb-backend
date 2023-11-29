package pl.edu.agh.websocket.service

import arrow.core.Either
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import pl.edu.agh.auth.domain.WebSocketUserParams

object WebSocketMainLoop {

    suspend fun <T> WebSocketSession.startMainLoop(
        logger: Logger,
        kSerializer: KSerializer<T>,
        webSocketUserParams: WebSocketUserParams,
        initPlayer: suspend (WebSocketUserParams, WebSocketSession) -> Either<String, Unit>,
        closeConnection: suspend (WebSocketUserParams) -> Unit,
        block: suspend (WebSocketUserParams, T) -> Unit
    ) {
        try {
            initPlayer(webSocketUserParams, this)
                .onLeft {
                    close(reason = CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Gra nie rozpoczeta albo zakonczona"))
                    return
                }
        } catch (e: Exception) {
            logger.error("Init player thrown exception, $e", e)
        }
        try {
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    Either.catch {
                        Json.decodeFromString(kSerializer, text)
                    }.mapLeft {
                        logger.error("Error while decoding message: $it", it)
                    }.map {
                        block(webSocketUserParams, it)
                    }
                }
                if (frame is Frame.Ping) {
                    send(Frame.Pong(frame.buffer))
                }
            }
        } catch (e: Exception) {
            logger.error("Main loop have thrown exception: $e", e)
        } finally {
            logger.info("Closing connection with user ${webSocketUserParams.loginUserId}")
            try {
                closeConnection(webSocketUserParams)
            } catch (e: Exception) {
                logger.error("Close connection thrown exception, $e", e)
            }
        }
    }
}
