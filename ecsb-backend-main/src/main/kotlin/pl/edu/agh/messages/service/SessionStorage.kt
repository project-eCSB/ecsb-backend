package pl.edu.agh.messages.service

import io.ktor.server.websocket.*
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId

interface SessionStorage<T> {
    fun addSession(gameSessionId: GameSessionId, playerId: PlayerId, webSocketServerSession: WebSocketServerSession)
    fun removeSession(gameSessionId: GameSessionId, user: PlayerId)
    fun getSessions(gameSessionId: GameSessionId): Map<PlayerId, WebSocketServerSession>?
}
