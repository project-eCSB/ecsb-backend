package pl.edu.agh.move

import io.ktor.websocket.*
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.game.service.GameUserService
import pl.edu.agh.game.service.GameUserServiceImpl
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.moving.domain.PlayerPosition
import pl.edu.agh.redis.RedisJsonConnector

object MovingModule {
    fun getKoinMovingModule(
        moveSessionStorage: SessionStorage<WebSocketSession>,
        redisMovementDataConnector: RedisJsonConnector<PlayerId, PlayerPosition>,
    ): Module = module {
        single<GameUserService> { GameUserServiceImpl(redisMovementDataConnector) }
        single<SessionStorage<WebSocketSession>> { moveSessionStorage }
        single<MovementDataConnector> { MovementDataConnector(redisMovementDataConnector) }
    }
}
