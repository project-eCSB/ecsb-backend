package pl.edu.agh.messages.service

import arrow.core.NonEmptySet
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.raise.option
import io.ktor.websocket.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.LoggerDelegate

interface MessagePasser<T> {
    suspend fun broadcast(gameSessionId: GameSessionId, senderId: PlayerId, message: T)
    suspend fun unicast(gameSessionId: GameSessionId, fromId: PlayerId, toId: PlayerId, message: T)
    suspend fun multicast(gameSessionId: GameSessionId, fromId: PlayerId, toIds: NonEmptySet<PlayerId>, message: T)
}

class WebSocketMessagePasser<T>(
    private val sessionStorage: SessionStorage<WebSocketSession>,
    private val kSerializer: KSerializer<T>
) : MessagePasser<T> {
    private val logger by LoggerDelegate()

    override suspend fun broadcast(gameSessionId: GameSessionId, senderId: PlayerId, message: T) {
        logger.info("Broadcasting message $message from $senderId")
        sessionStorage.getSessions(gameSessionId)?.forEach { (user, session) ->
            if (user != senderId) {
                session.outgoing.send(Frame.Text(Json.encodeToString(kSerializer, message)))
            }
        }
    }

    override suspend fun unicast(gameSessionId: GameSessionId, fromId: PlayerId, toId: PlayerId, message: T) {
        logger.info("Unicasting message $message from $fromId to $toId")
        sessionStorage
            .getSessions(gameSessionId)
            ?.get(toId)
            ?.outgoing
            ?.send(Frame.Text(Json.encodeToString(kSerializer, message)))
    }

    override suspend fun multicast(
        gameSessionId: GameSessionId,
        fromId: PlayerId,
        toIds: NonEmptySet<PlayerId>,
        message: T
    ) {
        logger.info("Multicasting message $message from $fromId to $toIds")
        option {
            val sessions = Option.fromNullable(sessionStorage.getSessions(gameSessionId)).bind()
            toIds.forEach { playerId ->
                sessions[playerId]
                    ?.outgoing
                    ?.send(
                        Frame.Text(Json.encodeToString(kSerializer, message))
                    )
            }
        }.getOrElse { logger.warn("Game session $gameSessionId not found") }
    }
}
