package pl.edu.agh.auth.service

import arrow.core.NonEmptyList
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import java.time.Instant.now
import java.util.*

typealias JWTTokenSimple = String

interface GameAuthService {
    fun getGameUserToken(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        playerId: PlayerId,
        userRoles: NonEmptyList<Role>
    ): JWTTokenSimple
}

class GameAuthServiceImpl(private val jwtConfig: JWTConfig<Token.GAME_TOKEN>) : GameAuthService {
    override fun getGameUserToken(
        gameSessionId: GameSessionId,
        loginUserId: LoginUserId,
        playerId: PlayerId,
        userRoles: NonEmptyList<Role>
    ): JWTTokenSimple {
        return JWT
            .create()
            .withAudience(jwtConfig.audience)
            .withIssuer(jwtConfig.domain)
            .withExpiresAt(Date.from(now().plusSeconds(3600)))
            .withClaim("loginUserId", loginUserId.value)
            .withClaim("gameSessionId", gameSessionId.value)
            .withClaim("playerId", playerId.value)
            .withClaim("roles", userRoles.map { it.roleName })
            .sign(Algorithm.HMAC256(jwtConfig.secret))
    }
}
