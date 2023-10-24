package pl.edu.agh.game.service

import arrow.core.Either
import arrow.core.Option
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerStatus

interface GameUserService {
    suspend fun getGameUserStatus(gameSessionId: GameSessionId, loginUserId: LoginUserId): Option<PlayerStatus>
    suspend fun removePlayerFromGameSession(gameSessionId: GameSessionId, loginUserId: LoginUserId, inGame: Boolean)
    suspend fun setInGame(gameSessionId: GameSessionId, loginUserId: LoginUserId): Either<String, Unit>
}