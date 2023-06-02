package pl.edu.agh.chat

import io.ktor.server.application.*
import io.ktor.websocket.*
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import pl.edu.agh.chat.domain.Message
import pl.edu.agh.chat.service.ProductionService
import pl.edu.agh.chat.service.ProductionServiceImpl
import pl.edu.agh.chat.service.TradeService
import pl.edu.agh.chat.service.TradeServiceImpl
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.messages.service.SessionStorageImpl
import pl.edu.agh.messages.service.WebSocketMessagePasser
import pl.edu.agh.redis.InteractionDataConnector
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.redis.getRedisConfig

object ChatModule {
    fun Application.getKoinChatModule() = module {
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
        single<RedisHashMapConnector<GameSessionId, PlayerId, InteractionStatus>> {
            RedisHashMapConnector(
                getRedisConfig(),
                RedisHashMapConnector.INTERACTION_DATA_PREFIX,
                GameSessionId::toName,
                PlayerId.serializer(),
                InteractionStatus.serializer()
            )
        }
        single { InteractionDataConnector(get()) }
        single<TradeService> { TradeServiceImpl() }
        single<ProductionService> { ProductionServiceImpl() }
    }
}
