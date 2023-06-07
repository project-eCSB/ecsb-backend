package pl.edu.agh.chat

import io.ktor.server.application.*
import io.ktor.websocket.*
import org.koin.dsl.module
import pl.edu.agh.chat.domain.InteractionDto
import pl.edu.agh.chat.domain.Message
import pl.edu.agh.chat.redis.InteractionDataConnector
import pl.edu.agh.chat.service.ProductionService
import pl.edu.agh.chat.service.ProductionServiceImpl
import pl.edu.agh.chat.service.TradeService
import pl.edu.agh.chat.service.TradeServiceImpl
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.domain.PlayerPosition
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.redis.RedisConfig
import pl.edu.agh.redis.RedisHashMapConnector

object ChatModule {
    fun Application.getKoinChatModule(
        redisConfig: RedisConfig,
        sessionStorage: SessionStorage<WebSocketSession>,
        messagePasser: MessagePasser<Message>
    ) = module {
        single<SessionStorage<WebSocketSession>> { sessionStorage }
        single<MessagePasser<Message>> { messagePasser }
        single<RedisHashMapConnector<GameSessionId, PlayerId, PlayerPosition>> {
            RedisHashMapConnector(
                redisConfig,
                RedisHashMapConnector.MOVEMENT_DATA_PREFIX,
                GameSessionId::toName,
                PlayerId.serializer(),
                PlayerPosition.serializer()
            )
        }
        single<RedisHashMapConnector<GameSessionId, PlayerId, InteractionDto>> {
            RedisHashMapConnector(
                redisConfig,
                RedisHashMapConnector.INTERACTION_DATA_PREFIX,
                GameSessionId::toName,
                PlayerId.serializer(),
                InteractionDto.serializer()
            )
        }
        single { InteractionDataConnector(get()) }
        single<TradeService> { TradeServiceImpl() }
        single<ProductionService> { ProductionServiceImpl() }
    }
}
