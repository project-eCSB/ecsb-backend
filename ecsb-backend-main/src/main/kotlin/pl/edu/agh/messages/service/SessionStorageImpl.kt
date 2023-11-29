package pl.edu.agh.messages.service

import io.ktor.websocket.*
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.LoggerDelegate
import java.util.concurrent.ConcurrentHashMap

class SessionStorageImpl : SessionStorage<WebSocketSession> {
    private val logger by LoggerDelegate()

    private val connections = ConcurrentHashMap<GameSessionId, ConcurrentHashMap<PlayerId, WebSocketSession>>()

    override fun addSession(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        webSocketServerSession: WebSocketSession
    ) {
        connections.getOrPut(gameSessionId) { ConcurrentHashMap() }[playerId] = webSocketServerSession
        logger.info("Connected new user $playerId to game $gameSessionId")
    }

    override fun removeSession(gameSessionId: GameSessionId, user: PlayerId) {
        logger.info("deleted session $user from $gameSessionId")
        connections[gameSessionId]?.remove(user)
    }

    override fun getSessions(gameSessionId: GameSessionId): Map<PlayerId, WebSocketSession>? {
        return connections[gameSessionId]
    }

    override fun getAllSessions(): Map<GameSessionId, Map<PlayerId, WebSocketSession>> = connections
}
