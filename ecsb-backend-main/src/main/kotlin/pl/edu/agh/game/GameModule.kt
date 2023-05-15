package pl.edu.agh.game

import io.ktor.server.application.*
import org.koin.dsl.module
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.GameAuthService
import pl.edu.agh.auth.service.GameAuthServiceImpl
import pl.edu.agh.auth.service.getJWTConfig
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.game.service.GameService
import pl.edu.agh.game.service.GameServiceImpl
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.redis.getRedisConfig

object GameModule {
    fun Application.getKoinGameModule() = module {
        single<RedisHashMapConnector<GameSessionId, PlayerId, PlayerPosition>> {
            RedisHashMapConnector(
                getRedisConfig(),
                RedisHashMapConnector.MOVEMENT_DATA_PREFIX,
                GameSessionId::toName,
                PlayerId.serializer(),
                PlayerPosition.serializer()
            )
        }
        single<GameAuthService> { GameAuthServiceImpl(this@getKoinGameModule.getJWTConfig(Token.GAME_TOKEN)) }
        single<GameService> { GameServiceImpl(get(), get()) }
    }
}