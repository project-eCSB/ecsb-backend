package pl.edu.agh.production.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parZip
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.InteractionException
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.interaction.service.InteractionDataConnector
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import pl.edu.agh.utils.PosInt
import pl.edu.agh.utils.Transactor

interface ProductionService {
    suspend fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        quantity: PosInt,
        playerId: PlayerId
    ): Either<InteractionException, Unit>

    suspend fun setInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId)

    suspend fun removeInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId)
}

class ProductionServiceImpl(
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemInputMessage>
) : ProductionService {
    override suspend fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        quantity: PosInt,
        playerId: PlayerId
    ): Either<InteractionException, Unit> =
        Transactor.dbQuery {
            either {
                val (resourceName, unitPrice, maxProduction) = PlayerResourceDao.getPlayerWorkshopData(
                    gameSessionId,
                    playerId
                ).toEither { InteractionException.PlayerNotFound(gameSessionId, playerId) }.bind()

                val timeNeeded = (quantity.value + maxProduction.value - 1) / maxProduction.value

                PlayerResourceDao.conductPlayerProduction(
                    gameSessionId,
                    playerId,
                    resourceName,
                    quantity,
                    unitPrice,
                    timeNeeded.nonNeg
                )().mapLeft {
                    InteractionException.ProductionException.InsufficientResource(
                        playerId,
                        resourceName,
                        quantity.value
                    )
                }.bind()
            }
        }.map {
            parZip({
                interactionProducer.sendMessage(
                    gameSessionId,
                    playerId,
                    ChatMessageADT.SystemInputMessage.AutoCancelNotification.ProductionStart(playerId)
                )
            }, {
                removeInWorkshop(gameSessionId, playerId)
            }, { _, _ -> })
        }

    override suspend fun setInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId) {
        InteractionDataConnector.setInteractionData(
            gameSessionId,
            playerId,
            InteractionStatus.PRODUCTION_BUSY
        )
        interactionProducer.sendMessage(
            gameSessionId,
            playerId,
            ChatMessageADT.SystemInputMessage.WorkshopNotification.WorkshopChoosingStart(playerId)
        )
    }

    override suspend fun removeInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId) {
        InteractionDataConnector.removeInteractionData(gameSessionId, playerId)
        interactionProducer.sendMessage(
            gameSessionId,
            playerId,
            ChatMessageADT.SystemInputMessage.WorkshopNotification.WorkshopChoosingStop(playerId)
        )
    }
}
