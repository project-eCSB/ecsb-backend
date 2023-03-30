package pl.edu.agh.move.service

import io.ktor.server.sessions.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import pl.edu.agh.move.domain.Message
import pl.edu.agh.messages.domain.MessageSenderData
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.utils.LoggerDelegate

class MessagePasserImpl(private val sessionStorage: SessionStorage<MessageSenderData>) : MessagePasser<Message> {
    private val logger by LoggerDelegate()

    override suspend fun broadcast(senderId: MessageSenderData, message: Message) {
        logger.info("Broadcasting message $message from $senderId")
        sessionStorage.getSessions().forEach { (user, session) ->
            if (user != senderId) {
                session.outgoing.send(Frame.Text(Json.encodeToString(Message.serializer(), message)))
            }
        }
    }


    suspend fun unicast(sentBy: MessageSenderData, sentTo: MessageSenderData, message: Message) {
        sessionStorage.getSession(sentTo)?.outgoing?.send(
            Frame.Text(
                Json.encodeToString(Message.serializer(), message)
            )
        )
    }

}