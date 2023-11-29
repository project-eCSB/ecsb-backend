package pl.edu.agh.game.service

import arrow.core.NonEmptyList
import arrow.core.Option
import arrow.core.raise.Effect
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.game.domain.GameResults
import pl.edu.agh.game.domain.requests.GameInitParameters
import pl.edu.agh.game.domain.requests.GameJoinCodeRequest
import pl.edu.agh.game.domain.responses.GameJoinResponse
import pl.edu.agh.game.domain.responses.GameSessionView

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

    suspend fun getGameResults(gameSessionId: GameSessionId): Option<GameResults>
}
