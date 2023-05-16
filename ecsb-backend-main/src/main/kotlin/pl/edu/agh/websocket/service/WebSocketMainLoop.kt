package pl.edu.agh.websocket.service

import arrow.core.Either
import io.ktor.websocket.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import pl.edu.agh.auth.domain.WebSocketUserParams

object WebSocketMainLoop {

    suspend fun <T> WebSocketSession.startMainLoop(
        logger: Logger,
        kSerializer: KSerializer<T>,
        webSocketUserParams: WebSocketUserParams,
        initPlayer: suspend (WebSocketUserParams, WebSocketSession) -> Unit,
        closeConnection: suspend (WebSocketUserParams) -> Unit,
        block: suspend (WebSocketUserParams, T) -> Unit
    ) {
        initPlayer(webSocketUserParams, this)
        try {
            for (frame in incoming) {
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
            }
        } catch (e: Exception) {
            logger.error("Main loop have thrown exception: $e", e)
        } finally {
            logger.info("Closing connection with user ${webSocketUserParams.loginUserId}")
            closeConnection(webSocketUserParams)
        }
    }
}
