package pl.edu.agh.chat.service

import arrow.core.Either
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.domain.GameResourceName
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.game.dao.ProductionException
import pl.edu.agh.utils.Transactor

interface ProductionService {
    suspend fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        resourceName: GameResourceName,
        quantity: Int
    ): Either<ProductionException, Unit>
}

class ProductionServiceImpl : ProductionService {
    override suspend fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        resourceName: GameResourceName,
        quantity: Int
    ): Either<ProductionException, Unit> =
        Transactor.dbQuery {
            PlayerResourceDao.conductPlayerProduction(gameSessionId, loginUserId, resourceName, quantity)
        }
}
