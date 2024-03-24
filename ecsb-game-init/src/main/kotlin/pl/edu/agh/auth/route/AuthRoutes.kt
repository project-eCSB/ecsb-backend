package pl.edu.agh.auth.route

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.input.LoginRequest
import pl.edu.agh.auth.domain.output.LoginResponse
import pl.edu.agh.auth.service.AuthService
import pl.edu.agh.utils.Utils.handleOutput
import pl.edu.agh.utils.Utils.responsePair

object AuthRoutes {

    fun Application.configureAuthRoutes() {
        val authService by inject<AuthService>()

        routing {
            post("/register") {
                handleOutput(call) {
                    val userData = call.receive<LoginRequest>()
                    authService.signUpNewUser(userData)
                        .mapLeft { exception ->
                            exception.toResponsePairLogging()
                        }.responsePair(LoginResponse.serializer())
                }
            }

            post("/login") {
                handleOutput(call) {
                    val userData = call.receive<LoginRequest>()
                    authService.signInUser(userData)
                        .mapLeft { exception ->
                            exception.toResponsePairLogging()
                        }.responsePair(LoginResponse.serializer())
                }
            }
        }
    }
}
