package pl.edu.agh.auth.route

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import pl.edu.agh.auth.domain.LoginUserBasicData
import pl.edu.agh.auth.domain.LoginUserData
import pl.edu.agh.auth.service.AuthService
import pl.edu.agh.utils.LoggerDelegate
import pl.edu.agh.utils.Transactor
import pl.edu.agh.utils.Utils.handleOutput
import pl.edu.agh.utils.Utils.responsePair

object AuthRoutes {
    private val logger by LoggerDelegate()

    fun Application.configureAuthRoutes() {
        val authService by inject<AuthService>()

        routing {
            post("/register") {
                handleOutput(call) {
                    Transactor.dbQuery {
                        val userData = call.receive<LoginUserBasicData>()
                        val signedUserResponse = authService.signUpNewUser(userData)
                        signedUserResponse.mapLeft {
                            it.toResponsePairLogging()
                        }.responsePair(LoginUserData.serializer())
                    }
                }
            }

            post("/login") {
                handleOutput(call) {
                    Transactor.dbQuery {
                        val userData = call.receive<LoginUserBasicData>()
                        val signedUserResponse = authService.signInUser(userData)
                        signedUserResponse.mapLeft {
                            it.toResponsePairLogging()
                        }.responsePair(LoginUserData.serializer())
                    }
                }
            }
        }
    }

}