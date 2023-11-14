package pl.edu.agh.move

import io.ktor.websocket.*
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.edu.agh.auth.domain.Token
import pl.edu.agh.auth.service.GameAuthService
import pl.edu.agh.auth.service.GameAuthServiceImpl
import pl.edu.agh.auth.service.JWTConfig
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.game.service.GameUserService
import pl.edu.agh.game.service.GameUserServiceImpl
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.move.domain.MoveMessage
import pl.edu.agh.redis.RedisJsonConnector

object MovingModule {
    fun getKoinMovingModule(
        gameTokenConfig: JWTConfig<Token.GAME_TOKEN>,
        sessionStorage: SessionStorage<WebSocketSession>,
        redisMovementDataConnector: RedisJsonConnector<PlayerId, PlayerPosition>,
        moveMessageInteractionProducer: InteractionProducer<MoveMessage>
    ): Module = module {
        single<GameAuthService> { GameAuthServiceImpl(gameTokenConfig) }
        single<GameUserService> { GameUserServiceImpl(redisMovementDataConnector) }
        single<SessionStorage<WebSocketSession>> { sessionStorage }
        single<InteractionProducer<MoveMessage>> { moveMessageInteractionProducer }
        single { MovementDataConnector(redisMovementDataConnector) }
    }
}
