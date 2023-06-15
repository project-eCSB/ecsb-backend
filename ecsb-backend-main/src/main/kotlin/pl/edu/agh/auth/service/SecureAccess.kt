package pl.edu.agh.auth.service

import arrow.core.*
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.raise.either
import arrow.core.raise.option
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.Utils.getOption
import pl.edu.agh.utils.getLogger

fun Application.configureSecurity(
    loginUserJwt: JWTConfig<Token.LOGIN_USER_TOKEN>,
    gameUserJwt: JWTConfig<Token.GAME_TOKEN>
) {
    install(Authentication) {
        fun jwtPA(role: Role) = run {
            this.jwt(
                Token.LOGIN_USER_TOKEN.suffix,
                role,
                loginUserJwt,
                nonEmptyListOf("name", "roles", "id"),
                Long.MAX_VALUE
            )

            this.jwt(
                Token.GAME_TOKEN.suffix,
                role,
                gameUserJwt,
                nonEmptyListOf("loginUserId", "gameSessionId", "playerId"),
                1800L
            )
        }

        jwtPA(Role.USER)
        jwtPA(Role.ADMIN)
    }
}

private fun getName(pair: Pair<Token, Role>): String {
    return pair.second.roleName + pair.first.suffix
}

fun Route.authenticate(vararg pairs: Pair<Token, Role>, build: Route.() -> Unit): Route {
    return authenticate(*pairs.map { getName(it) }.toTypedArray()) {
        build()
    }
}

fun Route.authenticate(token: Token, vararg roles: Role, build: Route.() -> Unit): Route {
    return authenticate(*roles.map { getName(token to it) }.toTypedArray()) {
        build()
    }
}

data class JWTConfig<T : Token>(val audience: String, val realm: String, val secret: String, val domain: String)

private fun JWTCredential.validateRole(role: Role): Either<String, JWTCredential> =
    if (payload.getClaim("roles").asList(String::class.java).map { Role.valueOf(it) }.contains(role)) {
        Right(this)
    } else {
        Left("Invalid role")
    }

fun <T : Token> AuthenticationConfig.jwt(
    suffix: String,
    name: Role,
    jwtConfig: JWTConfig<T>,
    claimPresence: NonEmptyList<String>,
    expiresAt: Long
) {
    jwt(name.roleName + suffix) {
        verifier(
            JWT.require(Algorithm.HMAC256(jwtConfig.secret)).withAudience(jwtConfig.audience)
                .withIssuer(jwtConfig.domain).run {
                    claimPresence.forEach {
                        this.withClaimPresence(it)
                    }
                    this
                }.acceptExpiresAt(expiresAt).build()
        )
        validate { credential ->
            val credentialEither: Either<String, JWTCredential> = either {
                credential.validateRole(name).bind()
                credential
            }

            credentialEither.fold(ifLeft = {
                getLogger(AuthenticationConfig::class.java).warn(it)
                null
            }, ifRight = {
                    JWTPrincipal(credential.payload)
                })
        }
        challenge { _, _ ->
            call.respond(
                io.ktor.http.HttpStatusCode.Unauthorized,
                "Unauthorized"
            )
        }
    }
}

suspend fun getLoggedUser(call: ApplicationCall): Triple<String, List<Role>, LoginUserId> {
    return getLoggedUser(call) { name, roles, userId -> Triple(name, roles, userId) }
}

fun getGameUser(call: ApplicationCall): Option<Triple<GameSessionId, LoginUserId, PlayerId>> = option {
    val payload = call.principal<JWTPrincipal>()?.payload.toOption().bind()

    val gameSessionId = Option.fromNullable(payload.getClaim("gameSessionId").asInt()).map { GameSessionId(it) }.bind()
    val userId = Option.fromNullable(payload.getClaim("loginUserId").asInt()).map { LoginUserId(it) }.bind()
    val playerId = Option.fromNullable(payload.getClaim("playerId").asString()).map { PlayerId(it) }.bind()

    Triple(gameSessionId, userId, playerId)
}

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

suspend fun <T> getLoggedUser(
    call: ApplicationCall,
    build: suspend (String, List<Role>, LoginUserId) -> T
): T {
    return option {
        val payload = call.principal<JWTPrincipal>()?.payload.toOption().bind()

        val name = payload.getClaim("name").asString()
        val roles = payload.getClaim("roles").asList(String::class.java).map { Role.valueOf(it) }
        val userId = LoginUserId(payload.getClaim("id").asInt())

        build(name, roles, userId)
    }.getOrNull()!!
}
