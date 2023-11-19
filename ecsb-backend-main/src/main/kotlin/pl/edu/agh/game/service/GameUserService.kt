package pl.edu.agh.game.service

import arrow.core.Either
import arrow.core.Option
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.moving.domain.PlayerStatus

interface GameUserService {
    suspend fun getGameUserStatus(gameSessionId: GameSessionId, loginUserId: LoginUserId): Option<PlayerStatus>
    suspend fun removePlayerFromGameSession(gameSessionId: GameSessionId, playerId: PlayerId, inGame: Boolean)
    suspend fun setInGame(gameSessionId: GameSessionId, playerId: PlayerId): Either<String, Unit>
}
