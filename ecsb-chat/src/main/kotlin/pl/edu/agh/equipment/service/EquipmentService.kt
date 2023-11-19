package pl.edu.agh.equipment.service

import arrow.core.Option
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.equipment.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor

interface EquipmentService {
    suspend fun getGameUserEquipment(
        gameSessionId: GameSessionId,
        playerId: PlayerId
    ): Option<PlayerEquipment>
}

class EquipmentServiceImpl : EquipmentService {
    private val logger by LoggerDelegate()

    override suspend fun getGameUserEquipment(
        gameSessionId: GameSessionId,
        playerId: PlayerId
    ): Option<PlayerEquipment> = Transactor.dbQuery {
        logger.info("Fetching equipment of user $playerId in game session $gameSessionId")
        PlayerResourceDao.getUserEquipment(gameSessionId, playerId)
    }
}
