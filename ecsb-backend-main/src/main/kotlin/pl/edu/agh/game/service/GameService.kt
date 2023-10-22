package pl.edu.agh.game.service

import arrow.core.NonEmptyList
import arrow.core.Option
import arrow.core.raise.Effect
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.GameResults
import pl.edu.agh.game.domain.`in`.GameInitParameters
import pl.edu.agh.game.domain.`in`.GameJoinCodeRequest
import pl.edu.agh.game.domain.out.GameJoinResponse
import pl.edu.agh.game.domain.out.GameSessionView

interface GameService {
    suspend fun getGameInfo(gameSessionId: GameSessionId): Option<GameSessionView>
    suspend fun joinToGame(
        gameJoinRequest: GameJoinCodeRequest,
        loginUserId: LoginUserId,
        userRoles: NonEmptyList<Role>
    ): Effect<JoinGameException, GameJoinResponse>

    suspend fun createGame(
        gameInitParameters: GameInitParameters,
        loginUserId: LoginUserId
    ): Effect<CreationException, GameSessionId>

    suspend fun copyGame(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        gameName: String
    ): Effect<CreationException, GameSessionId>

    suspend fun startGame(gameSessionId: GameSessionId): Option<Unit>

    suspend fun getGameResults(gameSessionId: GameSessionId): Option<GameResults>
}
