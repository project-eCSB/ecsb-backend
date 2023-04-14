package pl.edu.agh.messages.service

import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId

interface MessagePasser<T> {
    suspend fun broadcast(gameSessionId: GameSessionId, senderId: PlayerId, message: T)
    suspend fun unicast(gameSessionId: GameSessionId, fromId: PlayerId, toId: PlayerId, message: T)
}
