package pl.edu.agh.move

import arrow.core.Option
import arrow.core.filterOption
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.domain.GameClassName
import pl.edu.agh.move.domain.MessageADT
import pl.edu.agh.moving.PlayerPositionDto
import pl.edu.agh.moving.PlayerPositionWithClass
import pl.edu.agh.moving.domain.PlayerPosition
import pl.edu.agh.redis.RedisJsonConnector

class MovementDataConnector(private val redisJsonConnector: RedisJsonConnector<PlayerId, PlayerPositionDto>) {

    suspend fun getMovementData(sessionId: GameSessionId, playerId: PlayerId): Option<PlayerPosition> =
        redisJsonConnector.findOne(sessionId, playerId).map { it.toPlayerPosition() }

    suspend fun getAllMovementDataIfActive(sessionId: GameSessionId): List<PlayerPositionWithClass> =
        redisJsonConnector.getAll(sessionId).map { (_, playerPosition) ->
            playerPosition.toPlayerPositionWithClassIfActive()
        }.filterOption()

    suspend fun changeMovementData(
        sessionId: GameSessionId,
        playerId: PlayerId,
        gameClassName: GameClassName,
        playerMove: MessageADT.UserInputMessage.Move
    ) = setMovementData(
        sessionId,
        PlayerPositionDto(playerId, playerMove.coords, playerMove.direction, gameClassName, true)
    )

    suspend fun changeMovementData(
        sessionId: GameSessionId,
        gameClassName: GameClassName,
        playerMove: MessageADT.SystemInputMessage
    ) =
        when (playerMove) {
            is MessageADT.SystemInputMessage.PlayerAdded -> setMovementData(
                sessionId,
                PlayerPositionDto(playerMove.id, playerMove.coords, playerMove.direction, gameClassName, true)
            )

            is MessageADT.SystemInputMessage.PlayerRemove -> removeMovementData(sessionId, playerMove.id)
        }

    private suspend fun removeMovementData(sessionId: GameSessionId, playerId: PlayerId) =
        redisJsonConnector.removeElement(sessionId, playerId)

    private suspend fun setMovementData(sessionId: GameSessionId, movementData: PlayerPositionDto) =
        redisJsonConnector.changeData(sessionId, movementData.id, movementData)

    suspend fun setAsInactive(gameSessionId: GameSessionId, playerId: PlayerId) {
        redisJsonConnector.setUnsafeElement(gameSessionId, playerId, "$.isActive", "false")
    }

}
