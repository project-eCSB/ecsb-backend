package pl.edu.agh.production

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.some
import arrow.fx.coroutines.parZip
import kotlinx.serialization.KSerializer
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.InteractionException
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.domain.Money
import pl.edu.agh.equipment.service.PlayerResourceService
import pl.edu.agh.equipmentChangeQueue.dao.EquipmentChangeQueueDao
import pl.edu.agh.equipmentChangeQueue.domain.PlayerEquipmentAdditions
import pl.edu.agh.game.dao.ChangeValue
import pl.edu.agh.game.dao.GameUserDao
import pl.edu.agh.game.dao.PlayerEquipmentChanges
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.interaction.service.InteractionConsumer
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.production.domain.WorkshopInternalMessages
import pl.edu.agh.time.domain.TimestampMillis
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.seconds

class ProductionGameEngineService(
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    private val playerResourceService: PlayerResourceService,
) : InteractionConsumer<WorkshopInternalMessages> {
    private val logger by LoggerDelegate()
    private val timeout = 5.seconds
    override suspend fun callback(
        gameSessionId: GameSessionId,
        senderId: PlayerId,
        sentAt: LocalDateTime,
        message: WorkshopInternalMessages
    ) {
        when (message) {
            is WorkshopInternalMessages.WorkshopStart -> conductPlayerProduction(
                gameSessionId,
                message.amount,
                senderId
            ).onLeft {
                interactionProducer.sendMessage(
                    gameSessionId,
                    senderId,
                    ChatMessageADT.SystemOutputMessage.WorkshopMessages.WorkshopDeny(it.toResponsePairLogging().second)
                )
            }.onRight {
                interactionProducer.sendMessage(
                    gameSessionId,
                    senderId,
                    ChatMessageADT.SystemOutputMessage.WorkshopMessages.WorkshopAccept(
                        TimestampMillis(timeout.inWholeMilliseconds),
                    )
                )
            }
        }
    }

    override val tSerializer: KSerializer<WorkshopInternalMessages> = WorkshopInternalMessages.serializer()
    override fun consumeQueueName(hostTag: String) = "workshop-in-$hostTag"
    override fun exchangeName(): String = InteractionProducer.WORKSHOP_EXCHANGE
    override fun exchangeType(): ExchangeType = ExchangeType.SHARDING
    override fun autoDelete(): Boolean = true

    private suspend fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        quantity: PosInt,
        playerId: PlayerId
    ): Either<InteractionException, Unit> =
        either {
            logger.info("Conducting player production for player $playerId in game $gameSessionId")

            val statusWasSet = Transactor.dbQuery {
                GameUserDao.setUserBusyStatus(
                    gameSessionId,
                    playerId,
                    InteractionStatus.PRODUCTION_BUSY
                )()
            }

            raiseWhen(statusWasSet.not()) {
                InteractionException.CannotSetPlayerBusy(gameSessionId, playerId, InteractionStatus.PRODUCTION_BUSY)
            }

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
                    resources = nonEmptyMapOf(resourceName to ChangeValue(0.nonNeg, 0.nonNeg)),
                    time = ChangeValue(
                        0.nonNeg,
                        (quantity.value / maxProduction.value).nonNeg // This is probably buggy but fck it
                    )
                )
            ) { additionalActions ->
                parZip({
                    logger.info("Performed production for player $playerId in game $gameSessionId")
                    interactionProducer.sendMessage(
                        gameSessionId,
                        playerId,
                        ChatMessageADT.SystemOutputMessage.AutoCancelNotification.ProductionStart(timeout = timeout)
                    )
                }, {
                    Transactor.dbQuery {
                        EquipmentChangeQueueDao.addItemToProcessing(
                            TimestampMillis(timeout.inWholeMilliseconds),
                            gameSessionId,
                            playerId,
                            PlayerEquipmentAdditions(
                                money = Money(0),
                                resources = nonEmptyMapOf(resourceName to quantity.toNonNeg()).some()
                            ),
                            "workshop"
                        )()
                    }
                    logger.info("Added item to equipment change queue to processing for player $playerId in game $gameSessionId")
                }, {
                    removeInWorkshop(gameSessionId, playerId)
                }, {
                    additionalActions()
                }, { _, _, _, _ -> })
            }.mapLeft {
                InteractionException.ProductionException.InsufficientResource(
                    playerId,
                    resourceName,
                    quantity.value
                )
            }.bind()
        }

    private suspend fun removeInWorkshop(gameSessionId: GameSessionId, playerId: PlayerId) {
        InteractionDataService.instance.removeInteractionData(gameSessionId, playerId)
        interactionProducer.sendMessage(
            gameSessionId,
            playerId,
            ChatMessageADT.SystemOutputMessage.WorkshopMessages.WorkshopChoosingStop
        )
    }
}
