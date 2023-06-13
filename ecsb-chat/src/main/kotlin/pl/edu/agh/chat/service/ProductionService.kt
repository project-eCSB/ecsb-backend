package pl.edu.agh.chat.service

import arrow.core.Either
import arrow.core.raise.either
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.utils.Transactor

interface ProductionService {
    suspend fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        quantity: Int
    ): Either<InteractionException, Unit>
}

class ProductionServiceImpl : ProductionService {
    override suspend fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        quantity: Int
    ): Either<InteractionException, Unit> =
        Transactor.dbQuery {
            either {
                val (playerId, resourceName, actualMoney, unitPrice, maxProduction) = PlayerResourceDao.getPlayerData(
                    gameSessionId,
                    loginUserId
                ).toEither { InteractionException.PlayerNotFound(gameSessionId, loginUserId) }.bind()

                if (quantity <= 0) {
                    raise(
                        InteractionException.ProductionException.NegativeResource(
                            playerId,
                            resourceName,
                            quantity
                        )
                    )
                }

                if (actualMoney < unitPrice * quantity) {
                    raise(
                        InteractionException.ProductionException.TooLittleMoney(
                            playerId,
                            resourceName,
                            actualMoney,
                            quantity
                        )
                    )
                }

                if (quantity > maxProduction) {
                    raise(
                        InteractionException.ProductionException.TooManyUnits(
                            playerId,
                            resourceName,
                            quantity,
                            maxProduction
                        )
                    )
                }

                PlayerResourceDao.conductPlayerProduction(gameSessionId, playerId, resourceName, quantity, unitPrice)
            }
        }
}
