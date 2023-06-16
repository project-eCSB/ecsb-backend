package pl.edu.agh.chat.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parZip
import pl.edu.agh.chat.domain.InteractionDto
import pl.edu.agh.chat.domain.MessageADT
import pl.edu.agh.chat.redis.InteractionDataConnector
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.utils.Transactor

interface ProductionService {
    suspend fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        quantity: Int,
        playerId: PlayerId
    ): Either<InteractionException, Unit>

    suspend fun setInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId)

    suspend fun removeInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId)
}

class ProductionServiceImpl(
    private val interactionProducer: InteractionProducer,
    private val interactionDataConnector: InteractionDataConnector
) : ProductionService {
    override suspend fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        quantity: Int,
        playerId: PlayerId
    ): Either<InteractionException, Unit> =
        Transactor.dbQuery {
            either {
                val (resourceName, actualMoney, unitPrice, maxProduction) = PlayerResourceDao.getPlayerData(
                    gameSessionId,
                    playerId
                ).toEither { InteractionException.PlayerNotFound(gameSessionId, playerId) }.bind()

                if (quantity <= 0) {
                    raise(
                        InteractionException.ProductionException.NegativeResource(
                            playerId,
                            resourceName,
                            quantity
                        )
                    )
                }

                if (actualMoney < unitPrice * quantity) {
                    raise(
                        InteractionException.ProductionException.TooLittleMoney(
                            playerId,
                            resourceName,
                            actualMoney,
                            quantity
                        )
                    )
                }

                if (quantity > maxProduction) {
                    raise(
                        InteractionException.ProductionException.TooManyUnits(
                            playerId,
                            resourceName,
                            quantity,
                            maxProduction
                        )
                    )
                }

                PlayerResourceDao.conductPlayerProduction(gameSessionId, playerId, resourceName, quantity, unitPrice)
            }
        }.map {
            parZip({
                interactionProducer.sendMessage(
                    gameSessionId,
                    playerId,
                    MessageADT.SystemInputMessage.AutoCancelNotification.ProductionStart(playerId)
                )
            }, {
                removeInWorkshop(gameSessionId, playerId)
            }, { _, _ -> })
        }

    override suspend fun setInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId) {
        interactionDataConnector.setInteractionData(
            gameSessionId,
            playerId,
            InteractionDto(InteractionStatus.IN_WORKSHOP, playerId)
        )
        interactionProducer.sendMessage(
            gameSessionId,
            playerId,
            MessageADT.SystemInputMessage.WorkshopNotification.WorkshopChoosingStart(playerId)
        )
    }

    override suspend fun removeInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId) {
        interactionDataConnector.removeInteractionData(gameSessionId, playerId)
        interactionProducer.sendMessage(
            gameSessionId,
            playerId,
            MessageADT.SystemInputMessage.WorkshopNotification.WorkshopChoosingStop(playerId)
        )
    }
}
