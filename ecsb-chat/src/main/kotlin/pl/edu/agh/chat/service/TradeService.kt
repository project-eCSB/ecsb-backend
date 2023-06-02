package pl.edu.agh.chat.service

import arrow.core.Option
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor

interface TradeService {
    suspend fun getPlayerEquipment(gameSessionId: GameSessionId, playerId: PlayerId): Option<PlayerEquipment>
    suspend fun getPlayersEquipmentsForTrade(
        gameSessionId: GameSessionId,
        player1: PlayerId,
        player2: PlayerId
    ): Option<Pair<PlayerEquipment, PlayerEquipment>>

    suspend fun updatePlayerEquipment(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        equipmentChanges: PlayerEquipment
    )
}

class TradeServiceImpl : TradeService {
    private val logger by LoggerDelegate()

    override suspend fun getPlayerEquipment(gameSessionId: GameSessionId, playerId: PlayerId): Option<PlayerEquipment> =
        Transactor.dbQuery {
            logger.info("Fetching equipment of player $playerId in game session $gameSessionId")
            PlayerResourceDao.getUserEquipmentByPlayerId(gameSessionId, playerId)
        }

    override suspend fun getPlayersEquipmentsForTrade(
        gameSessionId: GameSessionId,
        player1: PlayerId,
        player2: PlayerId
    ): Option<Pair<PlayerEquipment, PlayerEquipment>> =
        Transactor.dbQuery {
            logger.info("Fetching equipments of players $player1 and $player2 for trade in game session $gameSessionId")
            PlayerResourceDao.getUsersEquipments(gameSessionId, player1, player2)
        }

    override suspend fun updatePlayerEquipment(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        equipmentChanges: PlayerEquipment
    ) =
        Transactor.dbQuery {
            logger.info("Updating equipment of player $playerId in game session $gameSessionId")
            PlayerResourceDao.updateResources(gameSessionId, playerId, equipmentChanges)
        }
}
