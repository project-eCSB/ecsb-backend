package pl.edu.agh.production.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parZip
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.InteractionException
import pl.edu.agh.domain.*
import pl.edu.agh.equipment.service.PlayerResourceService
import pl.edu.agh.game.dao.ChangeValue
import pl.edu.agh.game.dao.PlayerEquipmentChanges
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg

interface ProductionService {
    suspend fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        quantity: PosInt,
        playerId: PlayerId
    ): Either<InteractionException, Unit>

    suspend fun setInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId)

    suspend fun removeInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId)
    suspend fun changeSelectedValues(gameSessionId: GameSessionId, playerId: PlayerId, amount: NonNegInt)
}

class ProductionServiceImpl(
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    private val playerResourceService: PlayerResourceService,
    private val logsProducer: InteractionProducer<LogsMessage>
) : ProductionService {
    private val logger by LoggerDelegate()

    override suspend fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        quantity: PosInt,
        playerId: PlayerId
    ): Either<InteractionException, Unit> =
        either {
            val (resourceName, unitPrice, maxProduction) = Transactor.dbQuery {
                PlayerResourceDao.getPlayerWorkshopData(
                    gameSessionId,
                    playerId
                ).toEither { InteractionException.PlayerNotFound(gameSessionId, playerId) }.bind()
            }

            playerResourceService.conductEquipmentChangeOnPlayer(
                gameSessionId,
                playerId,
                PlayerEquipmentChanges(
                    money = ChangeValue(Money(0), Money((quantity * unitPrice).value.toLong())),
                    resources = nonEmptyMapOf(resourceName to ChangeValue(quantity.toNonNeg(), 0.nonNeg)),
                    time = ChangeValue(
                        0.nonNeg,
                        (quantity.value / maxProduction.value).nonNeg // This is probably buggy but fck it
                    )
                )
            ) { additionalActions ->
                parZip({
                    interactionProducer.sendMessage(
                        gameSessionId,
                        playerId,
                        ChatMessageADT.SystemOutputMessage.AutoCancelNotification.ProductionStart(playerId)
                    )
                }, {
                    removeInWorkshop(gameSessionId, playerId)
                }, {
                    additionalActions()
                }, { _, _, _ -> })
            }.mapLeft {
                InteractionException.ProductionException.InsufficientResource(
                    playerId,
                    resourceName,
                    quantity.value
                )
            }.bind()
        }

    override suspend fun setInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId) {
        InteractionDataService.instance.setInteractionData(
            gameSessionId,
            playerId,
            InteractionStatus.PRODUCTION_BUSY
        ).whenA({
            logger.error("Player already busy")
        }) {
            interactionProducer.sendMessage(
                gameSessionId,
                playerId,
                ChatMessageADT.SystemOutputMessage.WorkshopMessages.WorkshopChoosingStart(playerId)
            )
        }
    }

    override suspend fun removeInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId) {
        InteractionDataService.instance.removeInteractionData(gameSessionId, playerId)
        interactionProducer.sendMessage(
            gameSessionId,
            playerId,
            ChatMessageADT.SystemOutputMessage.WorkshopMessages.WorkshopChoosingStop(playerId)
        )
    }

    override suspend fun changeSelectedValues(gameSessionId: GameSessionId, playerId: PlayerId, amount: NonNegInt) {
        logsProducer.sendMessage(
            gameSessionId,
            playerId,
            LogsMessage.WorkshopChoosingChange(amount)
        )
    }
}
