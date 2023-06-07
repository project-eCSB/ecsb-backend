package pl.edu.agh.auth

import io.ktor.server.application.*
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.AuthService
import pl.edu.agh.auth.service.AuthServiceImpl
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.auth.service.TokenCreationService

object AuthModule {

    fun Application.getKoinAuthModule(
        jwt: JWTConfig<Token.LOGIN_USER_TOKEN>,
    ): Module =
        module {
            single { TokenCreationService(jwt) }
            single<AuthService> { AuthServiceImpl(get()) }
        }
}
