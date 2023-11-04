package pl.edu.agh.travel.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parZip
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.InteractionException
import pl.edu.agh.domain.*
import pl.edu.agh.equipment.service.PlayerResourceService
import pl.edu.agh.game.dao.ChangeValue
import pl.edu.agh.game.dao.PlayerEquipmentChanges
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.travel.dao.TravelDao
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg

interface TravelChoosingService {
    @Deprecated(message = "Use WebSocket instead of REST endpoint")
    suspend fun conductPlayerTravel(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        travelName: TravelName
    ): Either<InteractionException, Unit>

    suspend fun setInTravel(gameSessionId: GameSessionId, playerId: PlayerId)
    suspend fun removeInTravel(gameSessionId: GameSessionId, playerId: PlayerId)
    suspend fun changeTravelDestination(gameSessionId: GameSessionId, playerId: PlayerId, travelName: TravelName)
}

class TravelChoosingServiceImpl(
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    private val playerResourceService: PlayerResourceService,
    private val logsProducer: InteractionProducer<LogsMessage>
) : TravelChoosingService {
    private val logger by LoggerDelegate()

    @Deprecated("Use WebSocket instead of REST endpoint")
    override suspend fun conductPlayerTravel(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        travelName: TravelName
    ): Either<InteractionException, Unit> =
        either {
            val (_, timeNeeded, moneyRange, cityCosts) = Transactor.dbQuery {
                TravelDao.getTravelData(
                    gameSessionId,
                    travelName
                ).toEither {
                    InteractionException.TravelException.CityNotFound(
                        gameSessionId,
                        travelName
                    )
                }.bind()
            }

            val reward = PosInt((moneyRange.from.value..moneyRange.to.value).random())

            playerResourceService.conductEquipmentChangeOnPlayer(
                gameSessionId,
                playerId,
                PlayerEquipmentChanges(
                    money = ChangeValue(Money(reward.value.toLong()), Money(0)),
                    resources = cityCosts.map.mapValues { (_, value) -> ChangeValue(0.nonNeg, value) }
                        .toNonEmptyMapUnsafe(),
                    time = ChangeValue(0.nonNeg, timeNeeded.getOrNull()?.toNonNeg() ?: 0.nonNeg)
                )
            ) { additionalActions ->
                parZip({
                    interactionProducer.sendMessage(
                        gameSessionId,
                        playerId,
                        ChatMessageADT.SystemOutputMessage.AutoCancelNotification.TravelStart(playerId)
                    )
                }, {
                    removeInTravel(gameSessionId, playerId)
                }, {
                    additionalActions()
                }, { _, _, _ -> })
            }.mapLeft {
                InteractionException.TravelException.InsufficientResources(
                    playerId,
                    gameSessionId,
                    travelName
                )
            }.bind()
        }

    override suspend fun setInTravel(gameSessionId: GameSessionId, playerId: PlayerId) {
        InteractionDataService.instance.setInteractionData(
            gameSessionId,
            playerId,
            InteractionStatus.TRAVEL_BUSY
        ).whenA({
            logger.error("Player already busy")
        }) {
            interactionProducer.sendMessage(
                gameSessionId,
                playerId,
                ChatMessageADT.SystemOutputMessage.TravelChoosing.TravelChoosingStart(playerId)
            )
        }
    }

    override suspend fun removeInTravel(gameSessionId: GameSessionId, playerId: PlayerId) {
        InteractionDataService.instance.removeInteractionData(gameSessionId, playerId)
        interactionProducer.sendMessage(
            gameSessionId,
            playerId,
            ChatMessageADT.SystemOutputMessage.TravelChoosing.TravelChoosingStop(playerId)
        )
    }

    override suspend fun changeTravelDestination(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        travelName: TravelName
    ) {
        logsProducer.sendMessage(
            gameSessionId,
            playerId,
            LogsMessage.TravelChange(travelName)
        )
    }
}
