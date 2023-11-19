package pl.edu.agh.chat

import io.ktor.websocket.*
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.coop.service.CoopService
import pl.edu.agh.logs.domain.LogsMessage
import pl.edu.agh.equipment.service.EquipmentService
import pl.edu.agh.equipment.service.EquipmentServiceImpl
import pl.edu.agh.game.service.*
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.landingPage.domain.LandingPageMessage
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.production.domain.WorkshopInternalMessages
import pl.edu.agh.production.route.ProductionRoute
import pl.edu.agh.production.service.ProductionChoosingService
import pl.edu.agh.production.service.ProductionChoosingServiceImpl
import pl.edu.agh.trade.domain.TradeInternalMessages
import pl.edu.agh.trade.service.TradeService
import pl.edu.agh.travel.route.TravelRoute
import pl.edu.agh.travel.service.TravelChoosingService
import pl.edu.agh.travel.service.TravelChoosingServiceImpl

object ChatModule {
    fun getKoinChatModule(
        sessionStorage: SessionStorage<WebSocketSession>,
        interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
        coopMessagesProducer: InteractionProducer<CoopInternalMessages.UserInputMessage>,
        tradeMessagesProducer: InteractionProducer<TradeInternalMessages.UserInputMessage>,
        workshopMessagesProducer: InteractionProducer<WorkshopInternalMessages>,
        logsProducer: InteractionProducer<LogsMessage>,
        landingPageProducer: InteractionProducer<LandingPageMessage>
    ): Module = module {
        single<SessionStorage<WebSocketSession>> { sessionStorage }
        single<ProductionChoosingService> {
            ProductionChoosingServiceImpl(
                interactionProducer,
                logsProducer
            )
        }
        single<ProductionRoute> { ProductionRoute(get(), workshopMessagesProducer) }
        single<TravelChoosingService> {
            TravelChoosingServiceImpl(
                interactionProducer,
                logsProducer
            )
        }
        single<TravelRoute> { TravelRoute(get()) }
        single<TradeService> { TradeService(tradeMessagesProducer, interactionProducer) }
        single<GameStartService> { GameStartServiceImpl(landingPageProducer) }
        single<CoopService> { CoopService(coopMessagesProducer) }
        single<EquipmentService> { EquipmentServiceImpl() }
    }
}
