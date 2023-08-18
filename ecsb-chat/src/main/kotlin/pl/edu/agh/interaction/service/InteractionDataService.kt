package pl.edu.agh.interaction.service

import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.GameUserDao
import pl.edu.agh.utils.NonEmptyMap
import pl.edu.agh.utils.Transactor

interface InteractionDataService {
    suspend fun findOne(gameSessionId: GameSessionId, playerId: PlayerId) =
        Transactor.dbQuery {
            GameUserDao.getUserBusyStatus(gameSessionId, playerId)()
        }

    suspend fun removeInteractionData(sessionId: GameSessionId, playerId: PlayerId): Unit =
        Transactor.dbQuery {
            GameUserDao.setUserNotBusy(sessionId, playerId)()
        }

    suspend fun setInteractionData(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        interactionStatus: InteractionStatus
    ): Boolean = Transactor.dbQuery {
        GameUserDao.setUserBusyStatus(gameSessionId, playerId, interactionStatus)()
    }

    suspend fun setInteractionDataForPlayers(
        gameSessionId: GameSessionId,
        playerStatuses: NonEmptyMap<PlayerId, InteractionStatus>
    ): Boolean = Transactor.dbQuery {
        GameUserDao.setUserBusyStatuses(gameSessionId, playerStatuses)()
    }

    companion object {
        val instance: InteractionDataService = object : InteractionDataService {}
    }
}
