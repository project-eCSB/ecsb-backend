package pl.edu.agh.travel.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parZip
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.InteractionException
import pl.edu.agh.chat.domain.LogsMessage
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.domain.EquipmentInternalMessage
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.interaction.service.InteractionDataService
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.PosInt
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.whenA

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
    private val equipmentChangeProducer: InteractionProducer<EquipmentInternalMessage>,
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
                ).toEither { InteractionException.TravelException.CityNotFound(gameSessionId, travelName) }.bind()

                val cityCosts = PlayerResourceDao.getCityCosts(travelId)

                val reward = PosInt((minReward.value..maxReward.value).random())

                PlayerResourceDao.conductPlayerTravel(gameSessionId, playerId, cityCosts, reward, timeNeeded)()
                    .mapLeft {
                        InteractionException.TravelException.InsufficientResources(
                            playerId,
                            gameSessionId,
                            travelName
                        )
                    }.bind()
            }
        }.map {
            parZip({
                interactionProducer.sendMessage(
                    gameSessionId,
                    playerId,
                    ChatMessageADT.SystemOutputMessage.AutoCancelNotification.TravelStart(playerId)
                )
            }, {
                removeInTravel(gameSessionId, playerId)
            }, {
                equipmentChangeProducer.sendMessage(gameSessionId, playerId, EquipmentInternalMessage.EquipmentDetected)
            }, { _, _, _ -> })
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
