package pl.edu.agh.game

import org.koin.core.module.Module
import org.koin.dsl.module
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.GameAuthService
import pl.edu.agh.auth.service.GameAuthServiceImpl
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.game.service.*
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.landingPage.domain.LandingPageMessage
import pl.edu.agh.redis.RedisJsonConnector

object GameModule {
    fun getKoinGameModule(
        gameTokenConfig: JWTConfig<Token.GAME_TOKEN>,
        redisMovementDataConnector: RedisJsonConnector<PlayerId, PlayerPosition>,
        defaultAssets: GameAssets,
        logsProducer: InteractionProducer<LandingPageMessage>
    ): Module = module {
        single<GameAuthService> { GameAuthServiceImpl(gameTokenConfig) }
        single<GameUserService> { GameUserServiceImpl(redisMovementDataConnector) }
        single<GameService> { GameServiceImpl(get(), defaultAssets, logsProducer) }
    }

    fun getKoinGameUserModule(
        gameTokenConfig: JWTConfig<Token.GAME_TOKEN>,
        redisMovementDataConnector: RedisJsonConnector<PlayerId, PlayerPosition>
    ): Module = module {
        single<GameAuthService> { GameAuthServiceImpl(gameTokenConfig) }
        single<GameUserService> { GameUserServiceImpl(redisMovementDataConnector) }
    }
}
