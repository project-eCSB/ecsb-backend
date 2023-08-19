package pl.edu.agh.coop.service

import arrow.core.NonEmptySet
import arrow.core.Option
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.travel.dao.TravelDao
import pl.edu.agh.travel.domain.TravelName
import pl.edu.agh.travel.domain.out.GameTravelsView
import pl.edu.agh.utils.Transactor

interface TravelCoopService {
    suspend fun getTravelByName(gameSessionId: GameSessionId, travelName: TravelName): Option<GameTravelsView> =
        Transactor.dbQuery { TravelDao.getTravelByName(gameSessionId, travelName) }

    suspend fun getTravelByNames(
        gameSessionId: GameSessionId,
        names: NonEmptySet<TravelName>
    ): Option<NonEmptySet<GameTravelsView>> =
        Transactor.dbQuery { TravelDao.getTravelByNames(gameSessionId, names) }

    companion object {
        val instance = object : TravelCoopService {}
    }
}