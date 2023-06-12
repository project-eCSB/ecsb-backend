package pl.edu.agh.chat.service

import arrow.core.Either
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.chat.domain.MessageADT
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.PlayerResourceDao
import pl.edu.agh.game.dao.ProductionException
import pl.edu.agh.utils.Transactor

interface ProductionService {
    suspend fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        quantity: Int,
        playerId: PlayerId
    ): Either<ProductionException, Unit>
}

class ProductionServiceImpl(private val interactionProducer: InteractionProducer) : ProductionService {
    override suspend fun conductPlayerProduction(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        quantity: Int,
        playerId: PlayerId
    ): Either<ProductionException, Unit> =
        Transactor.dbQuery {
            PlayerResourceDao.conductPlayerProduction(gameSessionId, loginUserId, quantity)
        }.map {
            interactionProducer.sendMessage(
                gameSessionId,
                playerId,
                MessageADT.SystemInputMessage.AutoCancelNotification.ProductionStart(playerId)
            )
        }
}
