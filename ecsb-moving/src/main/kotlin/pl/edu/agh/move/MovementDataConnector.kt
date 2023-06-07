package pl.edu.agh.move

import arrow.core.Option
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.move.domain.MessageADT
import pl.edu.agh.redis.RedisHashMapConnector

class MovementDataConnector(private val redisHashMapConnector: RedisHashMapConnector<GameSessionId, PlayerId, PlayerPosition>) {

    suspend fun getMovementData(sessionId: GameSessionId, playerId: PlayerId): Option<PlayerPosition> =
        redisHashMapConnector.findOne(sessionId, playerId)
    suspend fun getAllMovementData(sessionId: GameSessionId): List<PlayerPosition> =
        redisHashMapConnector.getAll(sessionId).map { (_, playerPosition) ->
            playerPosition
        }

    suspend fun changeMovementData(
        sessionId: GameSessionId,
        playerId: PlayerId,
        playerMove: MessageADT.UserInputMessage.Move
    ) = setMovementData(
        sessionId,
        PlayerPosition(playerId, playerMove.coords, playerMove.direction)
    )

    suspend fun changeMovementData(sessionId: GameSessionId, playerMove: MessageADT.SystemInputMessage) =
        when (playerMove) {
            is MessageADT.SystemInputMessage.PlayerAdded -> setMovementData(
                sessionId,
                PlayerPosition(playerMove.id, playerMove.coords, playerMove.direction)
            )

            is MessageADT.SystemInputMessage.PlayerRemove -> removeMovementData(sessionId, playerMove.id)
        }

    private suspend fun removeMovementData(sessionId: GameSessionId, playerId: PlayerId) =
        redisHashMapConnector.removeElement(sessionId, playerId)

    private suspend fun setMovementData(sessionId: GameSessionId, movementData: PlayerPosition) =
        redisHashMapConnector.changeData(sessionId, movementData.id, movementData)
}
