package pl.edu.agh.interaction.service

import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.interaction.domain.InteractionDto
import pl.edu.agh.redis.RedisHashMapConnector

class InteractionDataConnector(private val redisHashMapConnector: RedisHashMapConnector<GameSessionId, PlayerId, InteractionDto>) {
    suspend fun findOne(gameSessionId: GameSessionId, playerId: PlayerId) =
        redisHashMapConnector.findOne(gameSessionId, playerId)

    suspend fun removeInteractionData(sessionId: GameSessionId, playerId: PlayerId) =
        redisHashMapConnector.removeElement(sessionId, playerId)

    suspend fun setInteractionData(
        sessionId: GameSessionId,
        playerId: PlayerId,
        interactionStatus: InteractionDto
    ) =
        redisHashMapConnector.changeData(sessionId, playerId, interactionStatus)
}
