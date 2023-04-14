package pl.edu.agh.move.service

import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.move.domain.Message
import pl.edu.agh.utils.LoggerDelegate

class MessagePasserImpl(private val sessionStorage: SessionStorage<WebSocketSession>) : MessagePasser<Message> {
    private val logger by LoggerDelegate()

    override suspend fun broadcast(gameSessionId: GameSessionId, senderId: PlayerId, message: Message) {
        logger.info("Broadcasting message $message from $senderId")
        sessionStorage.getSessions(gameSessionId)?.forEach { (user, session) ->
            if (user != senderId) {
                session.outgoing.send(Frame.Text(Json.encodeToString(Message.serializer(), message)))
            }
        }
    }

    override suspend fun unicast(gameSessionId: GameSessionId, fromId: PlayerId, toId: PlayerId, message: Message) {
        logger.info("Unicasting message $message from $fromId to $toId")
        sessionStorage
            .getSessions(gameSessionId)
            ?.get(toId)
            ?.outgoing
            ?.send(Frame.Text(Json.encodeToString(Message.serializer(), message)))
    }
}
