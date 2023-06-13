package pl.edu.agh.chat.service

import arrow.core.Either
import arrow.core.Option
import arrow.core.raise.either
import io.ktor.http.*
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.game.domain.GameResourceDto
import pl.edu.agh.utils.DomainException
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor

class TradeNegativeResource(playerId: PlayerId, gameResourceName: String, quantity: Int) : DomainException(
    HttpStatusCode.BadRequest,
    "Your trade offer includes negative resource $gameResourceName ($quantity)",
    "Player $playerId wanted to trade negative ($quantity) amount of $gameResourceName"
)

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

    fun getEquipmentChanges(
        player1: PlayerId,
        equipment1: PlayerEquipment,
        player2: PlayerId,
        equipment2: PlayerEquipment
    ): PlayerEquipment
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

    override fun getEquipmentChanges(
        player1: PlayerId,
        equipment1: PlayerEquipment,
        player2: PlayerId,
        equipment2: PlayerEquipment
    ): PlayerEquipment {
        verifyEquipment(equipment1, player1)
        verifyEquipment(equipment2, player2)
        val money = equipment1.money - equipment2.money
        val time = equipment1.time - equipment2.time
        val resources = equipment1.resources.zip(equipment2.resources)
            .map { (resource1, resource2) ->
                GameResourceDto(resource1.name, resource1.value - resource2.value)
            }
        return PlayerEquipment(money, time, resources)
    }

    private fun verifyEquipment(
        playerEquipment: PlayerEquipment,
        playerId: PlayerId
    ): Either<TradeNegativeResource, Unit> =
        either {
            val (money, time, resources) = playerEquipment
            if (money < 0) {
                raise(TradeNegativeResource(playerId, "money", money))
            }
            if (time < 0) {
                raise(TradeNegativeResource(playerId, "time", time))
            }
            resources.forEach { (name, value) ->
                if (value < 0) {
                    raise(TradeNegativeResource(playerId, name.value, value))
                }
            }
        }
}
