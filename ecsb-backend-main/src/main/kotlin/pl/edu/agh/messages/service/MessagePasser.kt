package pl.edu.agh.messages.service

import arrow.core.NonEmptySet
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.nonEmptySetOf
import arrow.core.raise.option
import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.LoggerDelegate

open class MessagePasser<T>(
    private val sessionStorage: SessionStorage<WebSocketSession>,
    private val kSerializer: KSerializer<T>
) {
    val logger by LoggerDelegate()

    private suspend fun send(session: WebSocketSession, frame: Frame.Text) {
        try {
            session.send(frame)
        } catch (e: CancellationException) {
            logger.warn("Message passer thrown exception, $e", e)
        } catch (e: Exception) {
            logger.error("Message passer thrown exception, $e", e)
        }
    }

    suspend fun broadcast(gameSessionId: GameSessionId, senderId: PlayerId, message: T) {
        logger.trace("[Sending] Broadcasting message {} from {}", message, senderId)
        sessionStorage.getSessions(gameSessionId)?.forEach { (user, session) ->
            if (user != senderId) {
                send(session, Frame.Text(Json.encodeToString(kSerializer, message)))
            }
        }
    }

    suspend fun unicast(gameSessionId: GameSessionId, fromId: PlayerId, toId: PlayerId, message: T) {
        val toIds = nonEmptySetOf(toId)
        logger.trace("[Sending] Multicasting message {} from {} to {}", message, fromId, toIds)
        multicast(gameSessionId, fromId, toIds, message)
    }

    suspend fun multicast(
        gameSessionId: GameSessionId,
        fromId: PlayerId,
        toIds: NonEmptySet<PlayerId>,
        message: T
    ) {
        logger.info("[Sending] Multicasting message $message from $fromId to $toIds")
        option {
            val sessions = Option.fromNullable(sessionStorage.getSessions(gameSessionId)).bind()
            toIds.forEach { playerId ->
                sessions[playerId]?.let {
                    send(it, Frame.Text(Json.encodeToString(kSerializer, message)))
                }
            }
        }.getOrElse { logger.info("Game session $gameSessionId not found") }
    }
}
