package pl.edu.agh.coop.redis

import arrow.core.getOrElse
import pl.edu.agh.coop.domain.CoopStates
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.redis.RedisJsonConnector

interface CoopStatesDataConnector {
    suspend fun getPlayerStates(gameSessionId: GameSessionId): Map<PlayerId, CoopStates>
    suspend fun getPlayerState(gameSessionId: GameSessionId, playerId: PlayerId): CoopStates
    suspend fun setPlayerState(gameSessionId: GameSessionId, playerId: PlayerId, newPlayerStatus: CoopStates)
}

class CoopStatesDataConnectorImpl(
    private val redisHashMapConnector: RedisJsonConnector<PlayerId, CoopStates>
) : CoopStatesDataConnector {
    override suspend fun getPlayerStates(gameSessionId: GameSessionId): Map<PlayerId, CoopStates> =
        redisHashMapConnector.getAll(gameSessionId)

    override suspend fun getPlayerState(gameSessionId: GameSessionId, playerId: PlayerId): CoopStates =
        redisHashMapConnector.findOne(gameSessionId, playerId).getOrElse { CoopStates.NoPlanningState }

    override suspend fun setPlayerState(gameSessionId: GameSessionId, playerId: PlayerId, newPlayerStatus: CoopStates) {
        redisHashMapConnector.changeData(gameSessionId, playerId, newPlayerStatus)
    }
}
