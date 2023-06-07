package pl.edu.agh.auth

import io.ktor.server.application.*
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.*

object AuthModule {

    fun Application.getKoinAuthModule(
        jwt: JWTConfig<Token.LOGIN_USER_TOKEN>,
        gameToken: JWTConfig<Token.GAME_TOKEN>
    ): Module =
        module {
            single { jwt }
            single { gameToken }
            singleOf(::TokenCreationService)
            single<AuthService> { AuthServiceImpl(get()) }
        }
}
