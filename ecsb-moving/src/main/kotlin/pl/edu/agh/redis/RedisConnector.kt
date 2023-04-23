package pl.edu.agh.redis

import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.newClient
import kotlinx.serialization.json.Json
import pl.edu.agh.domain.Coordinates
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.move.domain.MessageADT
import pl.edu.agh.move.domain.PlayerPosition
import pl.edu.agh.utils.LoggerDelegate

class RedisConnector(redisConfig: RedisConfig) {
    private val logger by LoggerDelegate()
    private val redisClient = newClient(Endpoint.from("${redisConfig.host}:${redisConfig.port}"))

    suspend fun getAllMovementData(sessionId: GameSessionId): MessageADT.OutputMessage.PlayersSync {
        logger.info("Requesting ${movementDataKey(sessionId)} all movement data")
        return redisClient.hgetAll(movementDataKey(sessionId)).map {
            it
        }.withIndex().partition {
            it.index % 2 == 0
        }.let { (even, odd) ->
            logger.info((even to odd).toString())
            even.map { it.value } zip odd.map { it.value }
        }.map { (key, value) ->
            val movementData = Json.decodeFromString(PlayerPosition.serializer(), value)
            val playerId = PlayerId(key)
            PlayerPosition(playerId, movementData.coords, movementData.direction)
        }.let {
            MessageADT.OutputMessage.PlayersSync(it)
        }
    }

    suspend fun changeMovementData(
        sessionId: GameSessionId,
        playerId: PlayerId,
        playerMove: MessageADT.UserInputMessage.Move
    ) {
        setMovementData(
            sessionId,
            PlayerPosition(playerId, playerMove.coords, playerMove.direction)
        )
    }

    suspend fun changeMovementData(sessionId: GameSessionId, playerMove: MessageADT.SystemInputMessage) {
        when (playerMove) {
            is MessageADT.SystemInputMessage.PlayerAdded -> setMovementData(
                sessionId,
                PlayerPosition(playerMove.id, playerMove.coords, playerMove.direction)
            )

            is MessageADT.SystemInputMessage.PlayerRemove -> removeMovementData(sessionId, playerMove.id)
        }
    }

    private suspend fun removeMovementData(sessionId: GameSessionId, playerId: PlayerId) {
        redisClient.hdel(movementDataKey(sessionId), playerId.value)
    }

    private suspend fun setMovementData(sessionId: GameSessionId, movementData: PlayerPosition) {
        redisClient.hset(
            movementDataKey(sessionId),
            movementData.id.value to Json.encodeToString(PlayerPosition.serializer(), movementData)
        )
    }

    companion object {
        private fun movementDataKey(sessionId: GameSessionId) = "movementData${sessionId.value}"
    }

    fun close() {
        redisClient.close()
    }
}
