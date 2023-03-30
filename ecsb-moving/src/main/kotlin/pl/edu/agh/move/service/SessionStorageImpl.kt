package pl.edu.agh.move.service

import io.ktor.server.websocket.*
import pl.edu.agh.messages.domain.MessageSenderData
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.utils.LoggerDelegate
import java.util.concurrent.atomic.AtomicInteger


class SessionStorageImpl : SessionStorage<MessageSenderData> {
    private val logger by LoggerDelegate()

    private val connections = mutableMapOf<MessageSenderData, WebSocketServerSession>()
    private val idCounter = AtomicInteger(0)

    override fun addSession(webSocketServerSession: WebSocketServerSession): MessageSenderData {
        val newSessionId = idCounter.addAndGet(1)
        val senderData = MessageSenderData(newSessionId)
        connections[senderData] = webSocketServerSession
        logger.info("connected new user $senderData")
        return senderData
    }

    override fun removeSession(user: MessageSenderData) {
        logger.info("deleted session $user")
        connections.remove(user)
    }

    override fun getSessions(): Map<MessageSenderData, WebSocketServerSession> {
        return connections
    }

    override fun getSession(user: MessageSenderData): WebSocketServerSession? {
        return connections[user]
    }
}