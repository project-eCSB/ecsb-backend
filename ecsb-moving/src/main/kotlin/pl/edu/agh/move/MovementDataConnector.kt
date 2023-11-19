package pl.edu.agh.move

import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.moving.domain.PlayerPosition
import pl.edu.agh.move.domain.MessageADT
import pl.edu.agh.redis.RedisJsonConnector

class MovementDataConnector(private val redisJsonConnector: RedisJsonConnector<PlayerId, PlayerPosition>) {

    suspend fun getAllMovementData(sessionId: GameSessionId): List<PlayerPosition> =
        redisJsonConnector.getAll(sessionId).map { (_, playerPosition) ->
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
        redisJsonConnector.removeElement(sessionId, playerId)

    private suspend fun setMovementData(sessionId: GameSessionId, movementData: PlayerPosition) =
        redisJsonConnector.changeData(sessionId, movementData.id, movementData)
}
