package pl.edu.agh.chat.service

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.zip
import pl.edu.agh.chat.domain.MessageADT
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.PosInt
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
                    .toEither { InteractionException.ResourcesException(gameSessionId, playerId) }.bind()

                val cityCosts = PlayerResourceDao.getCityCosts(travelId)

                playerResources.zip(cityCosts).forEach { (resourceName, pair) ->
                    val (first, second) = pair
                    if (first.value < second.value) {
                        raise(
                            InteractionException.TravelException.InsufficientResources(
                                playerId,
                                gameSessionId,
                                travelName,
                                resourceName,
                                first.value,
                                second.value
                            )
                        )
                    }
                }

                if (timeNeeded != null && timeNeeded.value > time.value) {
                    raise(
                        InteractionException.TravelException.InsufficientResources(
                            playerId,
                            gameSessionId,
                            travelName,
                            GameResourceName("time"),
                            time.value,
                            timeNeeded.value
                        )
                    )
                }

                val reward = PosInt((minReward.value..maxReward.value).random())

                PlayerResourceDao.conductPlayerTravel(gameSessionId, playerId, cityCosts, reward, timeNeeded)
            }
        }.map {
            interactionProducer.sendMessage(
                gameSessionId,
                playerId,
                MessageADT.SystemInputMessage.AutoCancelNotification.TravelStart(playerId)
            )
        }
}
