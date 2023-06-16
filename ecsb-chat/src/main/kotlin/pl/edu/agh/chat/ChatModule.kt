package pl.edu.agh.chat

import io.ktor.server.application.*
import io.ktor.websocket.*
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.edu.agh.chat.domain.InteractionDto
import pl.edu.agh.chat.domain.Message
import pl.edu.agh.chat.redis.InteractionDataConnector
import pl.edu.agh.chat.service.*
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.trade.service.TradeService
import pl.edu.agh.trade.service.TradeServiceImpl

object ChatModule {
    fun Application.getKoinChatModule(
        sessionStorage: SessionStorage<WebSocketSession>,
        messagePasser: MessagePasser<Message>,
        redisInteractionStatusConnector: RedisHashMapConnector<GameSessionId, PlayerId, InteractionDto>,
        interactionProducer: InteractionProducer
    ): Module = module {
        single<SessionStorage<WebSocketSession>> { sessionStorage }
        single<MessagePasser<Message>> { messagePasser }
        single<TradeService> {
            TradeServiceImpl(
                InteractionDataConnector(redisInteractionStatusConnector),
                interactionProducer
            )
        }
        single<ProductionService> {
            ProductionServiceImpl(
                interactionProducer,
                InteractionDataConnector(redisInteractionStatusConnector)
            )
        }
        single<TravelService> { TravelServiceImpl(interactionProducer) }
    }
}
