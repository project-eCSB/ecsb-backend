package pl.edu.agh.domain

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.toOption
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.utils.NonEmptyMap

class BusyStatusConnectorMock : InteractionDataService {

    private val mapOfStatuses = mutableMapOf<PlayerId, InteractionStatus>()

    override suspend fun removeInteractionData(sessionId: GameSessionId, playerId: PlayerId) {
        mapOfStatuses.remove(playerId)
    }

    override suspend fun findOne(gameSessionId: GameSessionId, playerId: PlayerId): Option<InteractionStatus> {
        return mapOfStatuses[playerId].toOption()
    }

    override suspend fun setInteractionData(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        interactionStatus: InteractionStatus
    ): Boolean {
        val currentStatus = findOne(gameSessionId, playerId).getOrElse { InteractionStatus.NOT_BUSY }
        if (currentStatus != InteractionStatus.NOT_BUSY && currentStatus != interactionStatus) {
            return false
        }
        mapOfStatuses[playerId] = interactionStatus
        return true
    }

    override suspend fun setInteractionDataForPlayers(
        gameSessionId: GameSessionId,
        playerStatuses: NonEmptyMap<PlayerId, InteractionStatus>
    ): Boolean {
        playerStatuses.forEach { (playerId, interactionStatus) ->
            val currentStatus = findOne(gameSessionId, playerId).getOrElse { InteractionStatus.NOT_BUSY }
            if (currentStatus != InteractionStatus.NOT_BUSY && currentStatus != interactionStatus) {
                return false
            }
        }
        playerStatuses.forEach { (playerId, interactionStatus) ->
            mapOfStatuses[playerId] = interactionStatus
        }
        return true
    }

    fun resetState() = mapOfStatuses.clear()
}
