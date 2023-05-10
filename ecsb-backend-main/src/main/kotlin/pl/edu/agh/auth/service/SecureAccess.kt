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
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.domain.WebSocketUserParams
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.utils.Utils.getOption
import pl.edu.agh.utils.getLogger

fun Application.configureSecurity() {
    val loginUserJwt = getJWTConfig()
    val gameUserJwt = getJWTConfig(Token.GAME_TOKEN.suffix)

    install(Authentication) {
        fun jwtPA(role: Role) = run {
            this.jwt(Token.LOGIN_USER.suffix, role, loginUserJwt, nonEmptyListOf("name", "roles", "id"), Long.MAX_VALUE)

            this.jwt(Token.GAME_TOKEN.suffix, role, gameUserJwt, nonEmptyListOf("loginUserId", "gameSessionId"), 1800L)
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

fun Application.getJWTConfig(prefix: String = "jwt"): JWTConfig {
    return JWTConfig(
        this.jwtAudience(prefix),
        this.jwtRealm(prefix),
        this.jwtSecret(prefix),
        this.jwtDomain(prefix)
    )
}

fun Application.getConfigProperty(path: String): String {
    return this.environment.config.property(path).getString()
}

data class JWTConfig(val audience: String, val realm: String, val secret: String, val domain: String)

private fun Application.jwtAudience(prefix: String): String {
    return this.getConfigProperty("$prefix.audience")
}

private fun Application.jwtRealm(prefix: String): String {
    return this.getConfigProperty("$prefix.realm")
}

private fun Application.jwtSecret(prefix: String): String {
    return this.getConfigProperty("$prefix.secret")
}

private fun Application.jwtDomain(prefix: String): String {
    return this.getConfigProperty("$prefix.domain")
}

private fun JWTCredential.validateRole(role: Role): Either<String, JWTCredential> =
    if (payload
            .getClaim("roles")
            .asList(String::class.java)
            .map { Role.valueOf(it) }
            .contains(role)
    ) {
        Right(this)
    } else {
        Left("Invalid role")
    }

fun AuthenticationConfig.jwt(
    suffix: String,
    name: Role,
    jwtConfig: JWTConfig,
    claimPresence: NonEmptyList<String>,
    expiresAt: Long
) {
    jwt(name.roleName + suffix) {
        verifier(
            JWT.require(Algorithm.HMAC256(jwtConfig.secret))
                .withAudience(jwtConfig.audience)
                .withIssuer(jwtConfig.domain)
                .run {
                    claimPresence.forEach {
                        this.withClaimPresence(it)
                    }
                    this
                }
                .acceptExpiresAt(expiresAt)
                .build()
        )
        validate { credential ->
            val credentialEither: Either<String, JWTCredential> = either {
                credential.validateRole(name).bind()
                credential
            }

            credentialEither.fold(
                ifLeft = {
                    getLogger(AuthenticationConfig::class.java).warn(it)
                    null
                },
                ifRight = {
                    JWTPrincipal(credential.payload)
                }
            )
        }
        challenge { _, _ ->
            call.respond(
                io.ktor.http.HttpStatusCode.Unauthorized,
                mapOf("error" to "Unauthorized")
            )
        }
    }
}

suspend fun getLoggedUser(call: ApplicationCall): Triple<String, List<Role>, LoginUserId> {
    return getLoggedUser(call) { name, roles, userId -> Triple(name, roles, userId) }
}

fun getGameUser(call: ApplicationCall): Option<Pair<GameSessionId, LoginUserId>> =
    option {
        val payload = call.principal<JWTPrincipal>()?.payload.toOption().bind()

        val gameSessionId =
            Option.fromNullable(payload.getClaim("gameSessionId").asInt()).map { GameSessionId(it) }.bind()
        val userId = Option.fromNullable(payload.getClaim("loginUserId").asInt()).map { LoginUserId(it) }.bind()

        gameSessionId to userId
    }

fun ApplicationCall.authWebSocketUser(): Either<String, WebSocketUserParams> {
    return either {
        val playerId =
            this@authWebSocketUser.parameters.getOption("name").map { PlayerId(it) }
                .toEither { "playerId is not present or malformed" }.bind()
        val gameSessionId =
            this@authWebSocketUser.parameters.getOption("gameSessionId")
                .flatMap { Option.fromNullable(it.toIntOrNull()) }
                .map { GameSessionId(it) }.toEither { "gameSessionId is not present or malformed" }.bind()

        WebSocketUserParams(playerId, gameSessionId)
    }
}

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
