package pl.edu.agh.trade.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.service.PlayerResourceService
import pl.edu.agh.game.dao.GameSessionUserClassesDao
import pl.edu.agh.game.dao.PlayerEquipmentChanges
import pl.edu.agh.trade.domain.TradeBid
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.nonEmptyMapOf

interface EquipmentTradeService {
    suspend fun validateResources(
        gameSessionId: GameSessionId,
        tradeBid: TradeBid
    ): Either<String, Unit>

    suspend fun finishTrade(
        gameSessionId: GameSessionId,
        finalBid: TradeBid,
        senderId: PlayerId,
        receiverId: PlayerId
    ): Either<String, Unit>
}

class EquipmentTradeServiceImpl(private val playerResourceService: PlayerResourceService) : EquipmentTradeService {
    override suspend fun validateResources(
        gameSessionId: GameSessionId,
        tradeBid: TradeBid
    ): Either<String, Unit> =
        either {
            val gameResourcesCount =
                Transactor.dbQuery { GameSessionUserClassesDao.getClasses(gameSessionId) }.map { map -> map.size }
                    .toEither { "Error getting resources from game session $gameSessionId" }.bind()
            val bidOffer = tradeBid.senderOffer.resources
            val bidRequest = tradeBid.senderRequest.resources
            ensure(bidOffer.keys.intersect(bidRequest.keys).size == bidOffer.size && bidOffer.size == gameResourcesCount) {
                "Wrong resource count"
            }
        }

    override suspend fun finishTrade(
        gameSessionId: GameSessionId,
        finalBid: TradeBid,
        senderId: PlayerId,
        receiverId: PlayerId
    ): Either<String, Unit> =
        either {
            validateResources(gameSessionId, finalBid).bind()

            val playerEquipmentChangesMap = nonEmptyMapOf(
                senderId to
                        PlayerEquipmentChanges.createFromEquipments(
                            finalBid.senderRequest.toPlayerEquipment(),
                            finalBid.senderOffer.toPlayerEquipment()
                        ),
                receiverId to
                        PlayerEquipmentChanges.createFromEquipments(
                            finalBid.senderOffer.toPlayerEquipment(),
                            finalBid.senderRequest.toPlayerEquipment()
                        )
            )

            playerResourceService.conductEquipmentChangeOnPlayers(
                gameSessionId,
                playerEquipmentChangesMap
            ) { it() }
                .mapLeft { (playerId, errors) ->
                    "Couldn't commit these changes in game ${gameSessionId.value} for player ${playerId.value} (${senderId.value} send ack), ${finalBid.senderRequest}, ${finalBid.senderOffer} because $errors"
                }.bind()
        }
}
