package pl.edu.agh.move

import io.ktor.server.application.*
import io.ktor.server.websocket.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.move.domain.Message
import pl.edu.agh.move.service.MessagePasserImpl
import pl.edu.agh.move.service.SessionStorageImpl
import pl.edu.agh.redis.RedisConfig
import pl.edu.agh.redis.RedisConnector

object MoveModule {
    fun Application.getKoinMoveModule(redisConfig: RedisConfig) = module {
        singleOf<SessionStorage<WebSocketServerSession>>(::SessionStorageImpl)
        single<MessagePasser<Message>> { MessagePasserImpl(get()) }
        single { RedisConnector(redisConfig) }
    }
}