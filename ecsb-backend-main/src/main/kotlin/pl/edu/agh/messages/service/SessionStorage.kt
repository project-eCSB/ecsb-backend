package pl.edu.agh.messages.service

import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId

interface SessionStorage<T> {
    fun addSession(gameSessionId: GameSessionId, playerId: PlayerId, webSocketServerSession: T)
    fun removeSession(gameSessionId: GameSessionId, user: PlayerId)
    fun getSessions(gameSessionId: GameSessionId): Map<PlayerId, T>?
    fun getAllSessions(): Map<GameSessionId, Map<PlayerId, T>>
}
