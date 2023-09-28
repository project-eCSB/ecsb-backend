package pl.edu.agh.trade.service

import arrow.core.Either
import arrow.core.Option
import arrow.core.raise.either
import arrow.core.raise.ensure
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerEquipment
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.GameSessionUserClassesDao
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.trade.domain.TradeBid
import pl.edu.agh.trade.domain.TradePlayerEquipment
import pl.edu.agh.utils.Transactor

interface EquipmentTradeService {
    suspend fun validateResources(
        gameSessionId: GameSessionId,
        tradeBid: TradeBid
    ): Either<String, Unit> = Transactor.dbQuery {
        either {
            val gameResourcesCount =
                GameSessionUserClassesDao.instance.getClasses(gameSessionId).map { map -> map.size }
                    .toEither { "Error getting resources from game session $gameSessionId" }.bind()
            val bidOffer = tradeBid.senderOffer.resources
            val bidRequest = tradeBid.senderRequest.resources
            ensure(bidOffer.keys.intersect(bidRequest.keys).size == bidOffer.size && bidOffer.size == gameResourcesCount) {
                "Wrong resource count"
            }
        }
    }

    suspend fun finishTrade(
        gameSessionId: GameSessionId,
        finalBid: TradeBid,
        senderId: PlayerId,
        receiverId: PlayerId
    ): Either<String, Unit> =
        Transactor.dbQuery {
            either {
                validateResources(gameSessionId, finalBid).bind()
                PlayerResourceDao.updateResources(
                    gameSessionId,
                    senderId,
                    finalBid.senderRequest.toPlayerEquipment(),
                    finalBid.senderOffer.toPlayerEquipment()
                )().mapLeft {
                    "Couldn't commit these changes $gameSessionId $senderId, ${finalBid.senderRequest}, ${finalBid.senderOffer}"
                }.bind()
                PlayerResourceDao.updateResources(
                    gameSessionId,
                    receiverId,
                    finalBid.senderOffer.toPlayerEquipment(),
                    finalBid.senderRequest.toPlayerEquipment()
                )().mapLeft {
                    "Couldn't commit these changes $gameSessionId $receiverId, ${finalBid.senderOffer}, ${finalBid.senderRequest}"
                }.bind()
            }
        }

    suspend fun getPlayersEquipmentsForTrade(
        gameSessionId: GameSessionId,
        player1: PlayerId,
        player2: PlayerId
    ): Map<PlayerId, TradePlayerEquipment> =
        Transactor.dbQuery {
            PlayerResourceDao.getUsersTradePlayerEquipments(gameSessionId, listOf(player1, player2))
        }

    companion object {
        val instance = object : EquipmentTradeService {}
    }
}
