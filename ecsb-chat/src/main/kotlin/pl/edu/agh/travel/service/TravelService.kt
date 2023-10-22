package pl.edu.agh.travel.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parZip
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.InteractionException
import pl.edu.agh.domain.LogsMessage
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.Money
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.service.PlayerResourceService
import pl.edu.agh.game.dao.ChangeValue
import pl.edu.agh.game.dao.PlayerEquipmentChanges
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.*
import pl.edu.agh.utils.NonNegInt.Companion.nonNeg

interface TravelService {
    suspend fun conductPlayerTravel(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        travelName: TravelName
    ): Either<InteractionException, Unit>

    suspend fun setInTravel(gameSessionId: GameSessionId, playerId: PlayerId)
    suspend fun removeInTravel(gameSessionId: GameSessionId, playerId: PlayerId)
    fun conductCoopPlayerTravel(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        gameCityName: TravelName
    ): Either<InteractionException, Unit>

    suspend fun changeTravelDestination(gameSessionId: GameSessionId, playerId: PlayerId, travelName: TravelName)
}

class TravelServiceImpl(
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
    private val playerResourceService: PlayerResourceService,
    private val logsProducer: InteractionProducer<LogsMessage>
) : TravelService {
    private val logger by LoggerDelegate()

    override fun conductCoopPlayerTravel(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        gameCityName: TravelName
    ): Either<InteractionException, Unit> {
        TODO("Not yet implemented")
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

    override suspend fun conductPlayerTravel(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        travelName: TravelName
    ): Either<InteractionException, Unit> =
        Transactor.dbQuery {
            either {
                val (minReward, maxReward, travelId, timeNeeded) = PlayerResourceDao.getTravelData(
                    gameSessionId,
                    travelName
                ).toEither {
                    InteractionException.TravelException.CityNotFound(
                        gameSessionId,
                        travelName
                    )
                }.bind()

                val cityCosts = PlayerResourceDao.getCityCosts(travelId)

                val reward = PosInt((minReward.value..maxReward.value).random())

                playerResourceService.conductEquipmentChangeOnPlayer(gameSessionId, playerId,
                    PlayerEquipmentChanges(
                        money = ChangeValue(Money(reward.value.toLong()), Money(0)),
                        resources = cityCosts.map.mapValues { (_, value) -> ChangeValue(0.nonNeg, value) }
                            .toNonEmptyMapUnsafe(),
                        time = ChangeValue(0.nonNeg, timeNeeded?.toNonNeg() ?: 0.nonNeg)
                    )
                ) { action ->
                    parZip({
                        interactionProducer.sendMessage(
                            gameSessionId,
                            playerId,
                            ChatMessageADT.SystemOutputMessage.AutoCancelNotification.TravelStart(playerId)
                        )
                    }, {
                        removeInTravel(gameSessionId, playerId)
                    }, {
                        action()
                    }, { _, _, _ -> })
                }.mapLeft {
                    InteractionException.TravelException.InsufficientResources(
                        playerId,
                        gameSessionId,
                        travelName
                    )
                }.bind()
            }
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
                ChatMessageADT.SystemOutputMessage.TravelNotification.TravelChoosingStart(playerId)
            )
        }
    }

    override suspend fun removeInTravel(gameSessionId: GameSessionId, playerId: PlayerId) {
        InteractionDataService.instance.removeInteractionData(gameSessionId, playerId)
        interactionProducer.sendMessage(
            gameSessionId,
            playerId,
            ChatMessageADT.SystemOutputMessage.TravelNotification.TravelChoosingStop(playerId)
        )
    }
}
