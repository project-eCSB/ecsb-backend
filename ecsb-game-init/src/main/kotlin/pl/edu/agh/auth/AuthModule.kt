package pl.edu.agh.auth

import org.koin.core.module.Module
import org.koin.dsl.module
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.AuthService
import pl.edu.agh.auth.service.AuthServiceImpl
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.auth.service.TokenCreationService

object AuthModule {

    fun getKoinAuthModule(
        jwt: JWTConfig<Token.LOGIN_USER_TOKEN>,
    ): Module =
        module {
            single { TokenCreationService(jwt) }
            single<AuthService> { AuthServiceImpl(get()) }
        }
}
