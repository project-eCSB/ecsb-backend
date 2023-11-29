package pl.edu.agh.auth.service

import arrow.core.Either
import arrow.core.flatMap
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import org.slf4j.LoggerFactory
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.LoginUserId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.Utils.getOption

private fun authWebSocketUser(
    jwtConfig: JWTConfig<Token.GAME_TOKEN>,
    plainToken: String
): Either<String, WebSocketUserParams> =
    Either.catch {
        val verifier = JWT.require(Algorithm.HMAC256(jwtConfig.secret))
            .withAudience(jwtConfig.audience)
            .withIssuer(jwtConfig.domain)
            .build()

        verifier.verify(plainToken)
        val decodedToken = JWT.decode(plainToken)

        val playerId = PlayerId(decodedToken.getClaim("playerId").asString())
        val gameSessionId = GameSessionId(decodedToken.getClaim("gameSessionId").asInt())
        val loginUserId = LoginUserId(decodedToken.getClaim("loginUserId").asInt())

        WebSocketUserParams(loginUserId, playerId, gameSessionId)
    }.mapLeft {
        val logger = LoggerFactory.getLogger(Application::class.java)
        logger.warn("Failed to authenticate user: ", it)
        "Failed to authenticate user"
    }

fun ApplicationCall.authWebSocketUserWS(jwtConfig: JWTConfig<Token.GAME_TOKEN>): Either<String, WebSocketUserParams> =
    parameters.getOption("gameToken").toEither { "Game token not found" }
        .flatMap { authWebSocketUser(jwtConfig, it) }
