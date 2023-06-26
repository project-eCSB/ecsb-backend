package pl.edu.agh.chat

import io.ktor.server.application.*
import io.ktor.websocket.*
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.chat.domain.Message
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.coop.service.CoopService
import pl.edu.agh.domain.GameSessionId
import pl.edu.agh.domain.InteractionStatus
import pl.edu.agh.domain.PlayerId
import pl.edu.agh.equipment.service.EquipmentService
import pl.edu.agh.equipment.service.EquipmentServiceImpl
import pl.edu.agh.interaction.service.InteractionDataConnector
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.messages.service.MessagePasser
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.production.route.ProductionRoute
import pl.edu.agh.production.service.ProductionService
import pl.edu.agh.production.service.ProductionServiceImpl
import pl.edu.agh.redis.RedisHashMapConnector
import pl.edu.agh.trade.domain.TradeInternalMessages
import pl.edu.agh.trade.service.TradeService
import pl.edu.agh.travel.route.TravelRoute
import pl.edu.agh.travel.service.TravelService
import pl.edu.agh.travel.service.TravelServiceImpl

object ChatModule {
    fun Application.getKoinChatModule(
        sessionStorage: SessionStorage<WebSocketSession>,
        messagePasser: MessagePasser<Message>,
        redisInteractionStatusConnector: RedisHashMapConnector<GameSessionId, PlayerId, InteractionStatus>,
        interactionProducer: InteractionProducer<ChatMessageADT.SystemInputMessage>,
        coopMessagesProducer: InteractionProducer<CoopInternalMessages>,
        tradeMessagesProducer: InteractionProducer<TradeInternalMessages.UserInputMessage>
    ): Module = module {
        single<SessionStorage<WebSocketSession>> { sessionStorage }
        single<MessagePasser<Message>> { messagePasser }
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
        single<TradeService> { TradeService(tradeMessagesProducer) }
        single<CoopService> { CoopService(coopMessagesProducer) }
        single<EquipmentService> { EquipmentServiceImpl() }
    }
}
