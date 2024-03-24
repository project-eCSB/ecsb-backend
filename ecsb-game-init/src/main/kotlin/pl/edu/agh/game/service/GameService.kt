package pl.edu.agh.game.service

import arrow.core.NonEmptyList
import arrow.core.Option
import arrow.core.raise.Effect
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.game.domain.GameResults
import pl.edu.agh.game.domain.requests.GameCreateRequest
import pl.edu.agh.game.domain.requests.GameJoinCodeRequest
import pl.edu.agh.game.domain.responses.GameJoinResponse
import pl.edu.agh.game.domain.responses.GameSettingsResponse

interface GameService {
    suspend fun getGameInfo(gameSessionId: GameSessionId): Option<GameSettingsResponse>
    suspend fun joinToGame(
        gameJoinRequest: GameJoinCodeRequest,
        loginUserId: LoginUserId,
        userRoles: NonEmptyList<Role>
    ): Effect<JoinGameException, GameJoinResponse>

    suspend fun createGame(
        gameCreateRequest: GameCreateRequest,
        loginUserId: LoginUserId
    ): Effect<CreationException, GameSessionId>

    suspend fun copyGame(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        gameName: String
    ): Effect<CreationException, GameSessionId>

    suspend fun getGameResults(gameSessionId: GameSessionId): Option<GameResults>
}
