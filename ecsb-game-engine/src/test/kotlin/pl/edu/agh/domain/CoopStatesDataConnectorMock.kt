package pl.edu.agh.domain

import pl.edu.agh.coop.domain.CoopStates
import pl.edu.agh.coop.redis.CoopStatesDataConnector

class CoopStatesDataConnectorMock : CoopStatesDataConnector {
    private val mapOfStates = mutableMapOf<PlayerId, CoopStates>()
    override suspend fun getPlayerState(gameSessionId: GameSessionId, playerId: PlayerId): CoopStates {
        return mapOfStates[playerId] ?: CoopStates.NoCoopState
    }

    override suspend fun setPlayerState(gameSessionId: GameSessionId, playerId: PlayerId, newPlayerStatus: CoopStates) {
        mapOfStates[playerId] = newPlayerStatus
    }

    fun resetState() = mapOfStates.clear()
}