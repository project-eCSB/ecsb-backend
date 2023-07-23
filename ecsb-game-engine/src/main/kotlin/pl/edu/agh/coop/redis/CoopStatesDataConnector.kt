package pl.edu.agh.coop.redis

import arrow.core.getOrElse
import pl.edu.agh.coop.domain.CoopStates
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.redis.RedisHashMapConnector

interface CoopStatesDataConnector {
    suspend fun getPlayerState(gameSessionId: GameSessionId, playerId: PlayerId): CoopStates
    suspend fun setPlayerState(gameSessionId: GameSessionId, playerId: PlayerId, newPlayerStatus: CoopStates)
}

class CoopStatesDataConnectorImpl(
    private val redisHashMapConnector: RedisHashMapConnector<PlayerId, CoopStates>
) : CoopStatesDataConnector {

    override suspend fun getPlayerState(gameSessionId: GameSessionId, playerId: PlayerId): CoopStates =
        redisHashMapConnector.findOne(gameSessionId, playerId).getOrElse { CoopStates.NoCoopState }

    override suspend fun setPlayerState(gameSessionId: GameSessionId, playerId: PlayerId, newPlayerStatus: CoopStates) {
        redisHashMapConnector.changeData(gameSessionId, playerId, newPlayerStatus)
    }
}
