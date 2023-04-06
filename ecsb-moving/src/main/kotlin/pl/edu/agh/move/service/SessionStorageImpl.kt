package pl.edu.agh.move.service

import io.ktor.server.websocket.*
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.utils.LoggerDelegate

class SessionStorageImpl : SessionStorage<WebSocketServerSession> {
    private val logger by LoggerDelegate()

    private val connections = mutableMapOf<GameSessionId, MutableMap<PlayerId, WebSocketServerSession>>()

    override fun addSession(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        webSocketServerSession: WebSocketServerSession
    ) {
        connections.getOrPut(gameSessionId) { mutableMapOf() }[playerId] = webSocketServerSession
        logger.info("Connected new user $playerId to game $gameSessionId")
    }

    override fun removeSession(gameSessionId: GameSessionId, user: PlayerId) {
        logger.info("deleted session $user fro $gameSessionId")
        connections[gameSessionId]?.remove(user)
    }

    override fun getSessions(gameSessionId: GameSessionId): Map<PlayerId, WebSocketServerSession>? {
        return connections[gameSessionId]
    }
}
