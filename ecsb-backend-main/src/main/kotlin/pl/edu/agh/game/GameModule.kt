package pl.edu.agh.game

import io.ktor.server.application.*
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.GameAuthService
import pl.edu.agh.auth.service.GameAuthServiceImpl
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.game.service.GameAssets
import pl.edu.agh.game.service.GameService
import pl.edu.agh.game.service.GameServiceImpl
import pl.edu.agh.redis.RedisHashMapConnector

object GameModule {
    fun Application.getKoinGameModule(
        gameTokenConfig: JWTConfig<Token.GAME_TOKEN>,
        redisMovementDataConnector: RedisHashMapConnector<PlayerId, PlayerPosition>,
        defaultAssets: GameAssets
    ): Module = module {
        single<GameAuthService> { GameAuthServiceImpl(gameTokenConfig) }
        single<GameService> { GameServiceImpl(redisMovementDataConnector, get(), defaultAssets) }
    }
}
