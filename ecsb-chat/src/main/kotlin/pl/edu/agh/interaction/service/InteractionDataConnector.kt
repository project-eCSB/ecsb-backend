package pl.edu.agh.interaction.service

import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.redis.RedisHashMapConnector

class InteractionDataConnector(private val redisHashMapConnector: RedisHashMapConnector<GameSessionId, PlayerId, InteractionStatus>) {
    suspend fun findOne(gameSessionId: GameSessionId, playerId: PlayerId) =
        redisHashMapConnector.findOne(gameSessionId, playerId)

    suspend fun removeInteractionData(sessionId: GameSessionId, playerId: PlayerId) =
        redisHashMapConnector.removeElement(sessionId, playerId)

    suspend fun setInteractionData(
        sessionId: GameSessionId,
        playerId: PlayerId,
        interactionStatus: InteractionStatus
    ) =
        redisHashMapConnector.changeData(sessionId, playerId, interactionStatus)
}
