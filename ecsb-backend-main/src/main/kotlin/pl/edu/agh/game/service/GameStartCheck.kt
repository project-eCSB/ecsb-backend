package pl.edu.agh.game.service

import arrow.core.Either
import arrow.core.none
import arrow.core.some
import org.slf4j.Logger
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.dao.GameSessionDao
import pl.edu.agh.utils.Transactor

object GameStartCheck {

    fun checkGameStartedAndNotEnded(
        gameSessionId: GameSessionId,
        playerId: PlayerId,
        action: suspend () -> Unit
    ): suspend (Logger) -> Either<String, Unit> = { logger ->
        Transactor.dbQuery {
            GameSessionDao.getGameSessionLeftTime(gameSessionId).flatMap {
                if (it.value <= 0) {
                    none()
                } else {
                    Unit.some()
                }
            }
        }.toEither { "Game session ended or not started" }
            .map {
                logger.info("Game can be accessed for $playerId in $gameSessionId")
                action()
            }
    }
}
