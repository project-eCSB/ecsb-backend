package pl.edu.agh.coop.redis

import arrow.core.getOrElse
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.trade.domain.TradeStates

interface TradeStatesDataConnector {
    suspend fun getPlayerState(gameSessionId: GameSessionId, playerId: PlayerId): TradeStates
    suspend fun setPlayerState(gameSessionId: GameSessionId, playerId: PlayerId, newPlayerStatus: TradeStates)
}

class TradeStatesDataConnectorImpl(
    private val redisHashMapConnector: RedisHashMapConnector<GameSessionId, PlayerId, TradeStates>
) : TradeStatesDataConnector {
    override suspend fun getPlayerState(gameSessionId: GameSessionId, playerId: PlayerId): TradeStates =
        redisHashMapConnector.findOne(gameSessionId, playerId).getOrElse { TradeStates.NoTradeState }

    override suspend fun setPlayerState(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        newPlayerStatus: TradeStates
    ) {
        redisHashMapConnector.changeData(gameSessionId, playerId, newPlayerStatus)
    }
}
