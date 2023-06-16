package pl.edu.agh.chat.service

import arrow.core.Either
import arrow.core.raise.either
import pl.edu.agh.chat.domain.MessageADT
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.Transactor

interface TravelService {
    suspend fun conductPlayerTravel(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        travelName: TravelName
    ): Either<InteractionException, Unit>
}

class TravelServiceImpl(private val interactionProducer: InteractionProducer) : TravelService {
    override suspend fun conductPlayerTravel(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        travelName: TravelName
    ): Either<InteractionException, Unit> =
        Transactor.dbQuery {
            either {
                val (_, time) = PlayerResourceDao.getPlayerMoneyAndTime(gameSessionId, playerId)
                    .toEither { InteractionException.PlayerNotFound(gameSessionId, playerId) }.bind()

                val (minReward, maxReward, travelId, timeNeeded) = PlayerResourceDao.getTravelData(
                    gameSessionId,
                    travelName
                ).toEither { InteractionException.TravelException.CityNotFound(gameSessionId, travelName) }.bind()

                val playerResources = PlayerResourceDao.getPlayerResources(gameSessionId, playerId)

                val cityCosts = PlayerResourceDao.getCityCosts(travelId)

                playerResources.zip(cityCosts).forEach { (playerResource, cityResource) ->
                    if (playerResource.value < cityResource.value) {
                        raise(
                            InteractionException.TravelException.InsufficientResources(
                                playerId,
                                gameSessionId,
                                travelName,
                                playerResource.name.value,
                                playerResource.value,
                                cityResource.value
                            )
                        )
                    }
                }

                if (timeNeeded != null && timeNeeded > time) {
                    raise(
                        InteractionException.TravelException.InsufficientResources(
                            playerId,
                            gameSessionId,
                            travelName,
                            "time",
                            time,
                            timeNeeded
                        )
                    )
                }

                val reward = (minReward..maxReward).random()

                PlayerResourceDao.conductPlayerTravel(gameSessionId, playerId, cityCosts, reward, time)
            }
        }.map {
            interactionProducer.sendMessage(
                gameSessionId,
                playerId,
                MessageADT.SystemInputMessage.AutoCancelNotification.TravelStart(playerId)
            )
        }
}
