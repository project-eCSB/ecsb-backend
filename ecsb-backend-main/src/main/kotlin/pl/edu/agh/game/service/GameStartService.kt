package pl.edu.agh.game.service

import arrow.core.Option
import arrow.core.raise.option
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerIdConst
import pl.edu.agh.game.dao.GameSessionDao
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.landingPage.domain.LandingPageMessage
import pl.edu.agh.utils.Transactor

interface GameStartService {
    suspend fun startGame(gameSessionId: GameSessionId): Option<Unit>
}

class GameStartServiceImpl(
    private val interactionProducer: InteractionProducer<LandingPageMessage>
) : GameStartService {
    override suspend fun startGame(gameSessionId: GameSessionId): Option<Unit> = option {
        Transactor.dbQuery {
            GameSessionDao.startGame(gameSessionId)()
        }.bind()
        interactionProducer.sendMessage(gameSessionId, PlayerIdConst.ECSB_CHAT_PLAYER_ID, LandingPageMessage.GameStarted)
    }
}
