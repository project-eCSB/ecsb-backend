package pl.edu.agh.production.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parZip
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.InteractionException
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.interaction.domain.InteractionDto
import pl.edu.agh.interaction.service.InteractionDataConnector
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.utils.NonNegInt
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
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemInputMessage>,
    private val interactionDataConnector: InteractionDataConnector
) : ProductionService {
    override suspend fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        quantity: PosInt,
        playerId: PlayerId
    ): Either<InteractionException, Unit> = either {
        val (resourceName, unitPrice, timeNeeded) = validateProductionMessage(gameSessionId, quantity, playerId).bind()
        Transactor.dbQuery {
            PlayerResourceDao.conductPlayerProduction(
                gameSessionId,
                playerId,
                resourceName,
                quantity,
                unitPrice,
                NonNegInt(timeNeeded)
            )
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

    private suspend fun validateProductionMessage(
        gameSessionId: GameSessionId,
        quantity: PosInt,
        playerId: PlayerId
    ): Either<InteractionException, Triple<GameResourceName, PosInt, Int>> =
        Transactor.dbQuery {
            either {
                val (resourceName, unitPrice, maxProduction, actualMoney, actualTime) = PlayerResourceDao.getPlayerData(
                    gameSessionId,
                    playerId
                ).toEither { InteractionException.PlayerNotFound(gameSessionId, playerId) }.bind()

                if (actualMoney.value < unitPrice.value * quantity.value) {
                    raise(
                        InteractionException.ProductionException.InsufficientResource(
                            playerId,
                            "money",
                            actualMoney.value,
                            resourceName,
                            quantity.value
                        )
                    )
                }

                val timeNeeded = (quantity.value + maxProduction.value - 1) / maxProduction.value

                if (actualTime.value < timeNeeded) {
                    raise(
                        InteractionException.ProductionException.InsufficientResource(
                            playerId,
                            "time",
                            actualTime.value,
                            resourceName,
                            quantity.value
                        )
                    )
                }

                Triple(resourceName, unitPrice, timeNeeded)
            }
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
            ChatMessageADT.SystemInputMessage.WorkshopNotification.WorkshopChoosingStart(playerId)
        )
    }

    override suspend fun removeInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId) {
        interactionDataConnector.removeInteractionData(gameSessionId, playerId)
        interactionProducer.sendMessage(
            gameSessionId,
            playerId,
            ChatMessageADT.SystemInputMessage.WorkshopNotification.WorkshopChoosingStop(playerId)
        )
    }
}
