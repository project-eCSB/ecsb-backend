package pl.edu.agh.chat

import io.ktor.websocket.*
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.edu.agh.auth.service.GameAuthService
import pl.edu.agh.chat.domain.ChatMessageADT
import pl.edu.agh.domain.LogsMessage
import pl.edu.agh.coop.domain.CoopInternalMessages
import pl.edu.agh.coop.service.CoopService
import pl.edu.agh.equipment.service.EquipmentService
import pl.edu.agh.equipment.service.EquipmentServiceImpl
import pl.edu.agh.equipment.service.PlayerResourceService
import pl.edu.agh.game.service.GameAssets
import pl.edu.agh.game.service.GameService
import pl.edu.agh.game.service.GameServiceImpl
import pl.edu.agh.interaction.service.InteractionProducer
import pl.edu.agh.landingPage.domain.LandingPageMessage
import pl.edu.agh.messages.service.SessionStorage
import pl.edu.agh.production.route.ProductionRoute
import pl.edu.agh.production.service.ProductionService
import pl.edu.agh.production.service.ProductionServiceImpl
import pl.edu.agh.time.domain.TimeInternalMessages
import pl.edu.agh.trade.domain.TradeInternalMessages
import pl.edu.agh.trade.service.TradeService
import pl.edu.agh.travel.route.TravelRoute
import pl.edu.agh.travel.service.TravelService
import pl.edu.agh.travel.service.TravelServiceImpl

object ChatModule {
    fun getKoinChatModule(
        gameAuthService: GameAuthService,
        defaultAssets: GameAssets,
        sessionStorage: SessionStorage<WebSocketSession>,
        interactionProducer: InteractionProducer<ChatMessageADT.SystemOutputMessage>,
        coopMessagesProducer: InteractionProducer<CoopInternalMessages>,
        tradeMessagesProducer: InteractionProducer<TradeInternalMessages.UserInputMessage>,
        playerResourceService: PlayerResourceService,
        logsProducer: InteractionProducer<LogsMessage>,
        timeProducer: InteractionProducer<TimeInternalMessages>,
        landingPageProducer: InteractionProducer<LandingPageMessage>
    ): Module = module {
        single<SessionStorage<WebSocketSession>> { sessionStorage }
        single<ProductionService> { ProductionServiceImpl(interactionProducer, playerResourceService, logsProducer) }
        single<ProductionRoute> { ProductionRoute(get()) }
        single<TravelService> { TravelServiceImpl(interactionProducer, playerResourceService, logsProducer) }
        single<TravelRoute> { TravelRoute(get()) }
        single<TradeService> { TradeService(tradeMessagesProducer, interactionProducer) }
        single<GameService> { GameServiceImpl(gameAuthService, defaultAssets, landingPageProducer) }
        single<CoopService> { CoopService(coopMessagesProducer) }
        single<EquipmentService> { EquipmentServiceImpl() }
        single<InteractionProducer<LogsMessage>> { logsProducer }
        single<InteractionProducer<TimeInternalMessages>> { timeProducer }
    }
}
