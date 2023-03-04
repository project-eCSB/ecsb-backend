package pl.edu.agh.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import java.util.*

fun Application.jwtAudience(): String {
    return this.environment.config.property("jwt.audience").getString()
}

fun Application.jwtRealm(): String {
    return this.environment.config.property("jwt.realm").getString()
}

fun Application.jwtSecret(): String {
    return this.environment.config.property("jwt.secret").getString()
}

fun Application.jwtDomain(): String {
    return this.environment.config.property("jwt.domain").getString()
}

fun Application.configureSecurity() {
    install(Authentication) {
        jwt("jwt") {
            val jwtAudience = this@configureSecurity.jwtAudience()
            realm = this@configureSecurity.jwtRealm()
            verifier(
                JWT
                    .require(
                        Algorithm.HMAC256(
                            this@configureSecurity.jwtSecret()
                        )
                    )
                    .withAudience(jwtAudience)
                    .withIssuer(this@configureSecurity.jwtDomain())
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(jwtAudience)) JWTPrincipal(credential.payload) else null
            }
            challenge { defaultScheme, realm ->
                call.respond(io.ktor.http.HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
            }
        }
    }
}

fun Application.createToken(name: String): String {
    return JWT.create()
        .withAudience(this.jwtAudience())
        .withIssuer(this.jwtDomain())
        .withClaim("name", name)
        .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
        .sign(Algorithm.HMAC256(this.jwtSecret()))
}
