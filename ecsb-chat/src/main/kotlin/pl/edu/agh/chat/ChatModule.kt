package pl.edu.agh.chat

import io.ktor.server.application.*
import io.ktor.websocket.*
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.Message
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.interaction.domain.InteractionDto
import pl.edu.agh.interaction.service.InteractionDataConnector
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.production.route.ProductionRoute
import pl.edu.agh.production.service.ProductionService
import pl.edu.agh.production.service.ProductionServiceImpl
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.trade.route.TradeRoute
import pl.edu.agh.trade.service.TradeService
import pl.edu.agh.trade.service.TradeServiceImpl
import pl.edu.agh.travel.route.TravelRoute
import pl.edu.agh.travel.service.TravelService
import pl.edu.agh.travel.service.TravelServiceImpl

object ChatModule {
    fun Application.getKoinChatModule(
        sessionStorage: SessionStorage<WebSocketSession>,
        messagePasser: MessagePasser<Message>,
        redisInteractionStatusConnector: RedisHashMapConnector<GameSessionId, PlayerId, InteractionDto>,
        interactionProducer: InteractionProducer<ChatMessageADT.SystemInputMessage>
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
        single<ProductionRoute> { ProductionRoute(get()) }
        single<TravelService> {
            TravelServiceImpl(
                interactionProducer,
                InteractionDataConnector(redisInteractionStatusConnector)
            )
        }
        single<TravelRoute> { TravelRoute(get()) }
        single<TradeRoute> { TradeRoute(messagePasser, get()) }
    }
}
