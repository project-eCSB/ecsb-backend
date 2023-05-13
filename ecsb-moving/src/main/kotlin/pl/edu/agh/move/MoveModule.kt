package pl.edu.agh.move

import io.ktor.server.application.*
import io.ktor.websocket.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.messages.service.SessionStorageImpl
import pl.edu.agh.messages.service.WebSocketMessagePasser
import pl.edu.agh.move.domain.Message
import pl.edu.agh.redis.MovementDataConnector
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.redis.getRedisConfig

object MoveModule {
    fun Application.getKoinMoveModule() = module {
        singleOf<SessionStorage<WebSocketSession>>(::SessionStorageImpl)
        single<MessagePasser<Message>> { WebSocketMessagePasser(get(), Message.serializer()) }
        single<RedisHashMapConnector<GameSessionId, PlayerId, PlayerPosition>> {
            RedisHashMapConnector(
                getRedisConfig(),
                RedisHashMapConnector.MOVEMENT_DATA_PREFIX,
                GameSessionId::toName,
                PlayerId.serializer(),
                PlayerPosition.serializer()
            )
        }
        single { MovementDataConnector(get()) }
    }
}
