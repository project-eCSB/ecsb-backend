package pl.edu.agh.auth.service

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.raise.either
import arrow.core.raise.option
import arrow.core.toOption
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.LoginUserId
import pl.edu.agh.auth.domain.Role
import pl.edu.agh.utils.getLogger

fun Application.configureSecurity() {
    val jwtConfig by inject<JWTConfig>()

    install(Authentication) {
        fun jwtPA(role: Role) = run {
            jwt(
                role, jwtConfig
            )
        }

        jwtPA(Role.USER)
        jwtPA(Role.ADMIN)
    }
}

fun Route.authenticate(vararg roles: Role, build: Route.() -> Unit): Route {
    return authenticate(*roles.map { it.roleName }.toTypedArray()) {
        build()
    }
}

fun Application.getJWTConfig(): JWTConfig {
    return JWTConfig(
        this.jwtAudience(), this.jwtRealm(), this.jwtSecret(), this.jwtDomain()
    )
}

fun Application.getConfigProperty(path: String): String {
    return this.environment.config.property(path).getString()
}


data class JWTConfig(val audience: String, val realm: String, val secret: String, val domain: String)

private fun Application.jwtAudience(): String {
    return this.getConfigProperty("jwt.audience")
}

private fun Application.jwtRealm(): String {
    return this.getConfigProperty("jwt.realm")
}

private fun Application.jwtSecret(): String {
    return this.getConfigProperty("jwt.secret")
}

private fun Application.jwtDomain(): String {
    return this.getConfigProperty("jwt.domain")
}

private fun JWTCredential.validateRole(role: Role): Either<String, JWTCredential> =
    if (payload
            .getClaim("roles")
            .asList(String::class.java)
            .map { Role.valueOf(it) }
            .contains(role)
    ) Right(this) else Left("Invalid role")


fun AuthenticationConfig.jwt(name: Role, jwtConfig: JWTConfig) {
    jwt(name.roleName) {
        verifier(
            JWT.require(Algorithm.HMAC256(jwtConfig.secret))
                .withAudience(jwtConfig.audience)
                .withIssuer(jwtConfig.domain)
                .withClaimPresence("name")
                .withClaimPresence("roles")
                .withClaimPresence("id")
                .acceptExpiresAt(Long.MAX_VALUE) // so everytime Expires at is ok
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
                ifRight = { JWTPrincipal(credential.payload) }
            )
        }
        challenge { _, _ ->
            call.respond(
                io.ktor.http.HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized")
            )
        }
    }
}

suspend fun getLoggedUser(call: ApplicationCall): Triple<String, List<Role>, LoginUserId> {
    return getLoggedUser(call) { name, roles, userId -> Triple(name, roles, userId) }
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
