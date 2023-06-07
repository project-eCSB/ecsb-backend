package pl.edu.agh.game

import io.ktor.server.application.*
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.GameAuthService
import pl.edu.agh.auth.service.GameAuthServiceImpl
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.game.service.GameAssets
import pl.edu.agh.game.service.GameService
import pl.edu.agh.game.service.GameServiceImpl
import pl.edu.agh.redis.RedisConfig
import pl.edu.agh.redis.RedisHashMapConnector

object GameModule {
    fun Application.getKoinGameModule(
        redisConfig: RedisConfig,
        gameTokenConfig: JWTConfig<Token.GAME_TOKEN>,
        defaultAssets: GameAssets
    ): Module = module {
        single<RedisHashMapConnector<GameSessionId, PlayerId, PlayerPosition>> {
            RedisHashMapConnector(
                redisConfig,
                RedisHashMapConnector.MOVEMENT_DATA_PREFIX,
                GameSessionId::toName,
                PlayerId.serializer(),
                PlayerPosition.serializer()
            )
        }
        single<GameAuthService> { GameAuthServiceImpl(gameTokenConfig) }
        single<GameService> { GameServiceImpl(get(), get(), defaultAssets) }
    }
}
