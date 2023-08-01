package pl.edu.agh.travel.service

import arrow.core.Either
import arrow.core.raise.either
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.InteractionException
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.interaction.service.InteractionDataConnector
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.travel.domain.TravelName
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
}

class TravelServiceImpl(
    private val interactionProducer: InteractionProducer<ChatMessageADT.SystemInputMessage>
) : TravelService {
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
            interactionProducer.sendMessage(
                gameSessionId,
                playerId,
                ChatMessageADT.SystemInputMessage.AutoCancelNotification.TravelStart(playerId)
            )
        }

    override suspend fun setInTravel(gameSessionId: GameSessionId, playerId: PlayerId) {
        InteractionDataConnector.setInteractionData(
            gameSessionId,
            playerId,
            InteractionStatus.TRAVEL_BUSY
        ).whenA({}) {
            interactionProducer.sendMessage(
                gameSessionId,
                playerId,
                ChatMessageADT.SystemInputMessage.TravelNotification.TravelChoosingStart(playerId)
            )
        }
    }

    override suspend fun removeInTravel(gameSessionId: GameSessionId, playerId: PlayerId) {
        InteractionDataConnector.removeInteractionData(gameSessionId, playerId)
        interactionProducer.sendMessage(
            gameSessionId,
            playerId,
            ChatMessageADT.SystemInputMessage.TravelNotification.TravelChoosingStop(playerId)
        )
    }
}
