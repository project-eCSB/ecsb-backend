package pl.edu.agh.move

import io.ktor.server.application.*
import io.ktor.websocket.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.move.domain.Message
import pl.edu.agh.move.service.MessagePasserImpl
import pl.edu.agh.redis.RedisConfig
import pl.edu.agh.redis.RedisConnector
import pl.edu.agh.messages.service.SessionStorageImpl

object MoveModule {
    fun Application.getKoinMoveModule(redisConfig: RedisConfig) = module {
        singleOf<SessionStorage<WebSocketSession>>(::SessionStorageImpl)
        single<MessagePasser<Message>> { MessagePasserImpl(get()) }
        single { RedisConnector(redisConfig) }
    }
}
