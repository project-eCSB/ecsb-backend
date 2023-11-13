package pl.edu.agh.game.service

import arrow.core.Either
import arrow.core.Option
import arrow.core.raise.either
import arrow.core.raise.option
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.moving.domain.PlayerPosition
import pl.edu.agh.moving.domain.PlayerStatus
import pl.edu.agh.game.dao.GameUserDao
import pl.edu.agh.moving.PlayerPositionDto
import pl.edu.agh.redis.RedisJsonConnector
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.raiseWhen

class GameUserServiceImpl(
    private val redisHashMapConnector: RedisJsonConnector<PlayerId, PlayerPositionDto>,
) : GameUserService {

    override suspend fun getGameUserStatus(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId
    ): Option<PlayerStatus> =
        option {
            val playerStatus = Transactor.dbQuery { GameUserDao.getGameUserInfo(loginUserId, gameSessionId) }.bind()
            redisHashMapConnector.setDataIfEmpty(
                gameSessionId,
                playerStatus.playerId,
                PlayerPositionDto(
                    playerStatus.playerId,
                    playerStatus.coords,
                    playerStatus.direction,
                    playerStatus.className,
                    isActive = false
                )
            )
            val maybeCurrentPosition = redisHashMapConnector.findOne(gameSessionId, playerStatus.playerId)

            maybeCurrentPosition.fold({ playerStatus }, { playerPosition ->
                playerStatus.copy(
                    coords = playerPosition.coords,
                    direction = playerPosition.direction
                )
            })
        }

    override suspend fun removePlayerFromGameSession(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        inGame: Boolean
    ) = Transactor.dbQuery {
        GameUserDao.updateUserInGame(gameSessionId, loginUserId, false)
        Unit
    }

    override suspend fun setInGame(gameSessionId: GameSessionId, loginUserId: LoginUserId): Either<String, Unit> =
        either {
            val rowsAffected = Transactor.dbQuery {
                GameUserDao.updateUserInGame(gameSessionId, loginUserId, true)
            }

            raiseWhen(rowsAffected == 0) { "Unable to join to game as " }
        }
}
