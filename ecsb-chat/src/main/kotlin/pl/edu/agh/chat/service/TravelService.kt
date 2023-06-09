package pl.edu.agh.chat.service

import arrow.core.Either
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.dao.InteractionException
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.utils.Transactor

interface TravelService {
    suspend fun conductPlayerTravel(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        travelName: TravelName
    ): Either<InteractionException, Unit>
}

class TravelServiceImpl : TravelService {
    override suspend fun conductPlayerTravel(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        travelName: TravelName
    ): Either<InteractionException, Unit> =
        Transactor.dbQuery {
            PlayerResourceDao.conductPlayerTravel(gameSessionId, loginUserId, travelName)
        }
}
